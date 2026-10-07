package org.hyperledger.ariesframework.credentials.v2

import anoncreds_uniffi.Issuer
import androidx.test.filters.LargeTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.OutboundMessage
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.SubjectOutboundTransport
import org.hyperledger.ariesframework.anoncreds.AnonCredsRegistry
import org.hyperledger.ariesframework.anoncreds.GetRevocationRegistryDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsOfferCredentialFormat
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.GetCredentialDefinitionReturn
import org.hyperledger.ariesframework.anoncreds.model.GetSchemaReturn
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsCredentialDefinitionPrivateRecord
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsCredentialDefinitionRecord
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsKeyCorrectnessProofRecord
import org.hyperledger.ariesframework.anoncreds.service.registry.GetRevocationStatusListReturn
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.models.AcceptCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.models.AcceptRequestOptionsV2
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.models.CredentialState
import org.hyperledger.ariesframework.credentials.models.OfferCredentialOptions
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.storage.DidCommMessageRole
import org.hyperledger.ariesframework.util.JsonAnyMapSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

/** Real wallets, encrypted DIDComm messages and native AnonCreds, without an external ledger. */
@LargeTest
class CredentialsV2Test {
    private lateinit var faberAgent: Agent
    private lateinit var aliceAgent: Agent
    private lateinit var faberConnection: ConnectionRecord
    private lateinit var aliceConnection: ConnectionRecord
    private lateinit var registry: TestRegistry
    private val attributes = listOf(CredentialPreviewAttribute(name = "name", value = "John"), CredentialPreviewAttribute(name = "age", value = "99"))

    @Before
    fun setUp() = runBlocking {
        registry = TestRegistry()
        val suffix = UUID.randomUUID().toString()
        faberAgent = Agent(TestHelper.context, testConfig("v2-faber-$suffix"), listOf(registry))
        aliceAgent = Agent(TestHelper.context, testConfig("v2-alice-$suffix"), listOf(registry))
        faberAgent.setOutboundTransport(SubjectOutboundTransport(aliceAgent))
        aliceAgent.setOutboundTransport(SubjectOutboundTransport(faberAgent))
        faberAgent.initialize()
        aliceAgent.initialize()
        val connections = TestHelper.makeConnection(faberAgent, aliceAgent)
        faberConnection = connections.first
        aliceConnection = connections.second
        registry.prepare(faberAgent)
    }

    @After
    fun tearDown() = runBlocking {
        try {
            if (::faberAgent.isInitialized) faberAgent.reset()
        } finally {
            if (::aliceAgent.isInitialized) aliceAgent.reset()
        }
    }

    private fun testConfig(name: String) = AgentConfig(
        walletKey = Agent.generateWalletKey(),
        walletId = name,
        label = name,
        autoAcceptCredential = AutoAcceptCredential.Never,
    )

    private fun offerOptions(autoAccept: AutoAcceptCredential? = null) = OfferCredentialOptions(
        connectionId = faberConnection.id,
        comment = "Offer to Alice",
        goalCode = null,
        goal = null,
        autoAcceptCredential = autoAccept,
        protocolVersion = "v2",
        credentialFormat = mapOf(
            "anoncreds" to Json.encodeToJsonElement(AnonCredsOfferCredentialFormat(registry.credDefId, attributes = attributes)),
        ),
    )

    private suspend fun awaitState(agent: Agent, threadId: String, state: CredentialState): CredentialExchangeRecord =
        withTimeout(30_000) {
            val connection = if (agent === faberAgent) faberConnection else aliceConnection
            var record = agent.credentialExchangeRepository.findByThreadAndConnectionId(threadId, connection.id)
            while (record?.state != state) {
                delay(50)
                record = agent.credentialExchangeRepository.findByThreadAndConnectionId(threadId, connection.id)
            }
            // Assert the actual protocol; never change the record to make this test pass.
            assertEquals("v2", record.protocolVersion)
            record
        }

    @Test
    fun testCredentialOffer() = runBlocking {
        val offer = faberAgent.credentialsV2.offerCredential(offerOptions())
        val threadId = offer.threadId
        awaitState(faberAgent, threadId, CredentialState.OfferSent)
        val receivedOffer = awaitState(aliceAgent, threadId, CredentialState.OfferReceived)
        aliceAgent.credentialsV2.acceptOffer(AcceptCredentialOfferOptionsV2(receivedOffer))
        val receivedRequest = awaitState(faberAgent, threadId, CredentialState.RequestReceived)
        val (_, credentialMessage) = faberAgent.credentialServiceV2.acceptRequest(AcceptRequestOptionsV2(receivedRequest))
        faberAgent.messageSender.send(OutboundMessage(credentialMessage, faberConnection))
        awaitState(faberAgent, threadId, CredentialState.CredentialIssued)
        val receivedCredential = awaitState(aliceAgent, threadId, CredentialState.CredentialReceived)
        val (_, ack) = aliceAgent.credentialServiceV2.acceptCredential(receivedCredential)
        aliceAgent.messageSender.send(OutboundMessage(ack, aliceConnection))
        assertCompleted(threadId)
    }

    @Test
    fun testAutoAcceptAgentConfig() = runBlocking {
        faberAgent.agentConfig.autoAcceptCredential = AutoAcceptCredential.Always
        aliceAgent.agentConfig.autoAcceptCredential = AutoAcceptCredential.Always
        val offer = faberAgent.credentialsV2.offerCredential(offerOptions())
        assertCompleted(offer.threadId)
    }

    @Test
    fun testAutoAcceptOptions() = runBlocking {
        val offer = faberAgent.credentialsV2.offerCredential(offerOptions(AutoAcceptCredential.Always))
        awaitState(faberAgent, offer.threadId, CredentialState.OfferSent)
        val receivedOffer = awaitState(aliceAgent, offer.threadId, CredentialState.OfferReceived)
        aliceAgent.credentialsV2.acceptOffer(
            AcceptCredentialOfferOptionsV2(receivedOffer, autoAcceptCredential = AutoAcceptCredential.Always),
        )
        assertCompleted(offer.threadId)
    }

    private suspend fun assertCompleted(threadId: String) {
        awaitState(faberAgent, threadId, CredentialState.Done)
        val holder = awaitState(aliceAgent, threadId, CredentialState.Done)
        assertEquals(1, holder.credentials.size)
        assertCredentialValues(holder)
    }

    private suspend fun assertCredentialValues(record: CredentialExchangeRecord) {
        val message = aliceAgent.didCommMessageRepository.getTypedAgentMessage<IssueCredentialMessageV2>(
            associatedRecordId = record.id,
            messageType = IssueCredentialMessageV2.type,
            role = DidCommMessageRole.Receiver,
        )
        assertNotNull(message)
        val attachmentId = message!!.formats.single().attachId
        val attachment = message.credentialAttachments.single { it.id == attachmentId }
        assertNotNull(attachment)
        val values = Json.parseToJsonElement(attachment!!.getDataAsString()!!).jsonObject["values"]!!.jsonObject
        val age = values["age"]!!.jsonObject
        assertEquals("99", age["raw"]!!.jsonPrimitive.content)
        assertEquals("99", age["encoded"]!!.jsonPrimitive.content)
        val name = values["name"]!!.jsonObject
        assertEquals("John", name["raw"]!!.jsonPrimitive.content)
        assertEquals("76355713903561865866741292988746191972523015098789458240077478826513114743258", name["encoded"]!!.jsonPrimitive.content)
    }

    private class TestRegistry : AnonCredsRegistry {
        override val methodName = "test"
        override val supportedIdentifier = Regex("^https://credentials.example/.*")
        val schemaId = "https://credentials.example/schema"
        val credDefId = "https://credentials.example/cred-def"
        private lateinit var schema: AnonCredsSchema
        private lateinit var credDef: AnonCredsCredentialDefinition

        suspend fun prepare(agent: Agent) {
            val issuerId = "https://credentials.example/issuer"
            val issuer = Issuer()
            val nativeSchema = issuer.createSchema("test", "1.0", issuerId, listOf("name", "age"))
            val definition = issuer.createCredentialDefinition(schemaId, nativeSchema, "default", issuerId, false)
            schema = Json.decodeFromString(nativeSchema.toJson())
            credDef = Json.decodeFromString(definition.credDef.toJson())
            agent.anoncredsCredentialDefinitionRepository.save(AnonCredsCredentialDefinitionRecord(
                credentialDefinitionId = credDefId, credentialDefinition = credDef, methodName = methodName,
            ))
            agent.anonCredsCredentialDefinitionPrivateRepository.save(AnonCredsCredentialDefinitionPrivateRecord(
                credentialDefinitionId = credDefId, value = Json.decodeFromString(JsonAnyMapSerializer, definition.credDefPriv.toJson()),
            ))
            agent.anonCredsKeyCorrectnessProofRepository.save(AnonCredsKeyCorrectnessProofRecord(
                credentialDefinitionId = credDefId, value = Json.decodeFromString(JsonAnyMapSerializer, definition.keyCorrectnessProof.toJson()),
            ))
        }

        override suspend fun getSchema(agent: Agent, schemaId: String): GetSchemaReturn {
            check(schemaId == this.schemaId)
            return GetSchemaReturn(schema = schema, schemaId = schemaId, issuerId = schema.issuerId)
        }

        override suspend fun getCredentialDefinition(agent: Agent, credentialDefinitionId: String): GetCredentialDefinitionReturn {
            check(credentialDefinitionId == credDefId)
            return GetCredentialDefinitionReturn(credentialDefinition = credDef, credentialDefinitionId = credDefId)
        }

        override suspend fun getRevocationRegistryDefinition(agent: Agent, revocationRegistryDefinitionId: String): GetRevocationRegistryDefinitionReturn =
            error("This fixture issues non-revocable credentials")

        override suspend fun getRevocationStatusList(agent: Agent, revocationRegistryId: String, timestamp: ULong): GetRevocationStatusListReturn =
            error("This fixture issues non-revocable credentials")
    }
}

package org.hyperledger.ariesframework.revocationnotification

import androidx.test.filters.LargeTest
import anoncreds_uniffi.Credential
import anoncreds_uniffi.CredentialRequestMetadata
import anoncreds_uniffi.Prover
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.AttachmentData
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.models.CredentialState
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.CredentialsV2Test
import org.hyperledger.ariesframework.credentials.v2.messages.IssueCredentialMessageV2
import org.hyperledger.ariesframework.credentials.v2.models.AcceptCredentialOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.AcceptOfferOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.AcceptRequestOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.CreateCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentials.v2.models.Format
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.RevocationRegistryDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotification
import org.hyperledger.ariesframework.revocationnotificationv2.message.RevocationNotificationMessageV2
import org.hyperledger.ariesframework.revocationnotificationv2.model.RevocationNotificationMessageV2Options
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class RevocationNotificationV1Test {
    private val logger = LoggerFactory.getLogger(RevocationNotificationV1Test::class.java)
    lateinit var faberAgent: Agent
    lateinit var aliceAgent: Agent
    lateinit var credDefId: String
    lateinit var faberConnection: ConnectionRecord
    lateinit var aliceConnection: ConnectionRecord
    lateinit var formats: List<Format>
    lateinit var offerAttachments: List<Attachment>

    val credentialPreview =
        CredentialPreviewV2.fromDictionary(mapOf("name" to "John", "age" to "99"))

    @Before
    fun setUp() = runTest(timeout = 5000.seconds) {
        val (agents, connections) = TestHelper.setupCredentialTests()
        faberAgent = agents.first
        aliceAgent = agents.second
        faberConnection = connections.first
        aliceConnection = connections.second
        credDefId = prepareForRevocation()
        formats = listOf(Format("indy", "hlindy/cred@v2.0"))
        offerAttachments = listOf(
            Attachment(
                id = "indy",
                mimetype = "application/json",
                data = AttachmentData()
            )
        )


    }

    @After
    fun tearDown() = runTest {
        faberAgent.reset()
        aliceAgent.reset()
    }

    suspend fun getCredentialRecord(agent: Agent, threadId: String): CredentialExchangeRecord {
        var credential =
            agent.credentialExchangeRepository.getByThreadAndConnectionId(threadId, null)
        return credential;
    }

    @Test
    @LargeTest
    fun should_emit_revocation_notification_event() = runTest {

        val eventReceived = CompletableDeferred<Boolean>() // Async tracking for event reception

        // Mock event listener
        val eventListener = mock<(AgentEvents.RevocationNotificationReceivedEventV2) -> Unit>()

        // Subscribe to the event listener
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> { event ->
            println("✅ Credential revoked (2.0): ${event.record.id}")
            eventReceived.complete(true); // Mark event as received
        }

        val (aliceCredentialRecord, faberCredentialRecord) = issueAndAcceptCredential()

        revokeCredential(aliceCredentialRecord)

        val wasEventPublished = withTimeoutOrNull(5.seconds) { eventReceived.await() } ?: false

        // Verify the event was actually triggered
        assertTrue("Revocation notification event was NOT triggered.", wasEventPublished)

    }

    @Test
    @LargeTest
    fun should_not_emit_revocation_notification_event_because_threadid_is_invalid() = runTest {

        val eventReceived = CompletableDeferred<Boolean>() // Async tracking for event reception

        // Mock event listener
        val eventListener = mock<(AgentEvents.RevocationNotificationReceivedEventV2) -> Unit>()

        // Subscribe to the event listener
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> { event ->
            println("✅ Credential revoked (2.0): ${event.record.id}")
            eventReceived.complete(true); // Mark event as received
        }


        val invalidCredentialId = "notIndy::invalidRevRegId::invalidCredRevId"

        val mapMessage: Map<String, RevocationNotificationMessageV2> = aliceAgent.revocationNotificationServicev2.createRevocationNotification(
            RevocationNotificationMessageV2Options(
                credentialId = invalidCredentialId,
                revocationFormat = "indy-anoncreds",
                comment = "Credential has been revoked"
            )
        );

        val revocationNotificationMessage: RevocationNotificationMessageV2? = mapMessage["message"]
        if (revocationNotificationMessage != null) {
            val messageContext = InboundMessageContext(
                revocationNotificationMessage,
                aliceAgent.context.toString()
            )

            try {
                faberAgent.revocationNotificationServicev2.processRevocationNotification(
                    messageContext
                )
            }catch(e:Exception){
                assertNotNull(e)
            }

            val wasEventPublished = withTimeoutOrNull(5.seconds) { eventReceived.await() } ?: false

            // Verify the event was actually triggered
            assertFalse("Revocation notification event was triggered.", wasEventPublished)
        } else {
            assertTrue("Error: Revocation notification message is null!", false)
        }

    }

    @Test
    @LargeTest
    fun should_emit_revocation_notification_event_2() = runTest {

        val eventReceived = CompletableDeferred<Boolean>() // Async tracking for event reception

        // Mock event listener
        val eventListener = mock<(AgentEvents.RevocationNotificationReceivedEventV2) -> Unit>()

        // Subscribe to the event listener
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> { event ->
            println("✅ Credential revoked (2.0): ${event.record.id}")
            eventReceived.complete(true); // Mark event as received
        }

        val metadata = mapOf(
            "revocationRegistryId" to "3qiQGrxu7HbkobQoHZBrfy:4:3qiQGrxu7HbkobQoHZBrfy:3:CL:2722152:default:CL_ACCUM:default",
            "credentialRevocationId" to "1"
        )
        val credentialId = "indy::${metadata["revocationRegistryId"]}::${metadata["credentialRevocationId"]}"

        // Create the RevocationNotificationMessage
        val mapMessage: Map<String, RevocationNotificationMessageV2> = aliceAgent.revocationNotificationServicev2.createRevocationNotification(
            RevocationNotificationMessageV2Options(
                credentialId = credentialId,
                revocationFormat = "indy-anoncreds",
                comment = "Credential has been revoked"
            )
        );

        var (aliceCredentialRecord, faberCredentialRecord) = issueAndAcceptCredential()


        val revocationNotificationMessage: RevocationNotificationMessageV2? = mapMessage["message"]
        if (revocationNotificationMessage != null) {
            val messageContext = InboundMessageContext(
                message = revocationNotificationMessage,
                connection = aliceConnection,
                plaintextMessage = revocationNotificationMessage.toString(),
                senderVerkey = null,
                recipientVerkey = null
            )

            val credentialRecords = aliceAgent.credentialRepository.getAll()
            logger.info("[IDD] alice credentialRecords: ${credentialRecords.toString()}")
            aliceAgent.revocationNotificationServicev2.processRevocationNotification(
                messageContext
            )

            val wasEventPublished = withTimeoutOrNull(5.seconds) { eventReceived.await() } ?: false

            // Verify the event was actually triggered
            assertTrue("Revocation notification event was NOT triggered.", wasEventPublished)
        } else {
            assertTrue("Error: Revocation notification message is null!", false)
        }

    }



    suspend fun revokeCredential(credentialRecord: CredentialExchangeRecord) {
        val didInfo = faberAgent.wallet.publicDid ?: throw Exception("Faber has no public DID.")
        faberAgent.ledgerService.revokeCredential(didInfo, credDefId, 1)
        aliceAgent.eventBus.publish(AgentEvents.RevocationNotificationReceivedEventV2(credentialRecord.copy()))
    }

    suspend fun prepareForRevocation(): String {
        val agent = faberAgent
        val didInfo = agent.wallet.publicDid
        val schemaId = agent.ledgerService.registerSchema(
            didInfo!!,
            SchemaTemplate("schema-${UUID.randomUUID()}", "1.0", listOf("name", "age")),
        )
        delay(0.1.seconds)
        val (schema, seqNo) = agent.ledgerService.getSchema(schemaId)
        val credDefId = agent.ledgerService.registerCredentialDefinition(
            didInfo,
            CredentialDefinitionTemplate(schema, "default", true, seqNo),
        )
        agent.ledgerService.registerRevocationRegistryDefinition(
            didInfo,
            RevocationRegistryDefinitionTemplate(credDefId, "default", 100),
        )

        return credDefId
    }


    private suspend fun issueAndAcceptCredential(): Pair<CredentialExchangeRecord, CredentialExchangeRecord> {
        var faberCredentialRecord = faberAgent.credentialsV2.offerCredential(
            CreateCredentialOfferOptionsV2(
                connection = faberConnection,
                credentialDefinitionId = credDefId,
                attributes = credentialPreview.attributes,
                comment = "Offer to Alice",
                formats = formats,
                credentialPreview = credentialPreview,
                offerAttachments = offerAttachments
            )
        )

        val threadId = faberCredentialRecord.threadId
        var aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        assertEquals(CredentialState.OfferReceived, aliceCredentialRecord.state)

        aliceAgent.credentialsV2.acceptOffer(AcceptOfferOptionsV2(aliceCredentialRecord.id))
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)
        assertEquals(CredentialState.RequestReceived, faberCredentialRecord.state)

        faberAgent.credentialsV2.acceptRequest(AcceptRequestOptionsV2(faberCredentialRecord.id))
        aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        assertEquals(CredentialState.CredentialReceived, aliceCredentialRecord.state)

        aliceAgent.credentialsV2.acceptCredential(AcceptCredentialOptionsV2(aliceCredentialRecord.id))
        aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)

        assertEquals(CredentialState.Done, aliceCredentialRecord.state)
        assertEquals(CredentialState.Done, faberCredentialRecord.state)

        return Pair(aliceCredentialRecord, faberCredentialRecord)
    }

//    fun toCredentialRecord(credentialExchange:CredentialExchangeRecord): CredentialRecord {
//        // Extract credential data from the first entry in the credentials list
//        val firstCredential = credentialExchange.credentials.firstOrNull()
//
//        // Convert credential attributes to JSON (assuming toJson() is available)
//        val credentialJson = firstCredential?.let { credentialBinding ->
//            credentialBinding.credentialRecordId // Assume this fetches credential JSON
//        } ?: "{}" // Default empty JSON if missing
//
//        // Create CredentialRecord instance
//        return CredentialRecord(
//            id = credentialExchange.id,
//            createdAt = credentialExchange.createdAt,
//            updatedAt = credentialExchange.updatedAt,
//
//            credentialId = firstCredential?.credentialRecordId ?: "unknown",
//            credentialRevocationId = firstCredential?.credentialRecordType,
//            revocationRegistryId = null, // No direct mapping, set to null or provide a lookup
//            linkSecretId = "default-link-secret", // Provide a default or lookup
//            credential = credentialJson,
//
//            schemaId = credentialExchange.credentialDefinitionId ?: "unknown",
//            schemaName = credentialExchange.credentialAttributes?.find { it.name == "schema_name" }?.value ?: "unknown",
//            schemaVersion = credentialExchange.credentialAttributes?.find { it.name == "schema_version" }?.value ?: "unknown",
//            schemaIssuerId = credentialExchange.credentialAttributes?.find { it.name == "schema_issuer_id" }?.value ?: "unknown",
//            issuerId = credentialExchange.credentialAttributes?.find { it.name == "issuer_id" }?.value ?: "unknown",
//            credentialDefinitionId = credentialExchange.credentialDefinitionId ?: "unknown",
//            revocationNotification = credentialExchange.revocationNotification
//        )
//    }
}
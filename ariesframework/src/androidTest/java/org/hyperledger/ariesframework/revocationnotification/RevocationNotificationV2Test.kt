package org.hyperledger.ariesframework.revocationnotification

import androidx.test.filters.LargeTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.hyperledger.ariesframework.*
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.AttachmentData
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.models.CredentialState
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.models.*
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.ledger.*
import org.hyperledger.ariesframework.revocationnotificationv2.model.RevocationNotificationMessageV2Options
import org.junit.*
import org.junit.Assert.*
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class RevocationNotificationV2Test {

    private val logger = LoggerFactory.getLogger(RevocationNotificationV2Test::class.java)

    private lateinit var faberAgent: Agent
    private lateinit var aliceAgent: Agent
    private lateinit var credDefId: String
    private lateinit var faberConnection: ConnectionRecord
    private lateinit var aliceConnection: ConnectionRecord
    private lateinit var formats: List<Format>
    private lateinit var offerAttachments: List<Attachment>

    private val credentialPreview = CredentialPreviewV2.fromDictionary(mapOf("name" to "John", "age" to "99"))

    @Before
    fun setUp() = runTest(timeout = 5000.seconds) {
        val (agents, connections) = TestHelper.setupCredentialTests()
        faberAgent = agents.first
        aliceAgent = agents.second
        faberConnection = connections.first
        aliceConnection = connections.second
        credDefId = prepareForRevocation()

        formats = listOf(Format("indy", "hlindy/cred@v2.0"))
        offerAttachments = listOf(Attachment(id = "indy", mimetype = "application/json", data = AttachmentData()))
    }

    @After
    fun tearDown() = runTest {
        faberAgent.reset()
        aliceAgent.reset()
    }

    /** Helper function to get a credential record */
    private suspend fun getCredentialRecord(agent: Agent, threadId: String): CredentialExchangeRecord =
        agent.credentialExchangeRepository.getByThreadAndConnectionId(threadId, null)

    /** Issues and accepts a credential between Faber and Alice */
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

    /** Helper function to test revocation event emission */
    private suspend fun testRevocationEvent(shouldEmit: Boolean, credentialIdValid: Boolean, credentialId: String? = null) {
        val eventReceived = CompletableDeferred<Boolean>()
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> {
            eventReceived.complete(true)
        }

        val (aliceCredentialRecord, _) = issueAndAcceptCredential()

        val credentialId = credentialId ?: aliceCredentialRecord.id
        val messageMap = aliceAgent.revocationNotificationServicev2.createRevocationNotification(
            RevocationNotificationMessageV2Options(
                credentialId = credentialId,
                revocationFormat = "indy-anoncreds",
                comment = "Credential has been revoked"
            )
        )

        val revocationNotificationMessage = messageMap["message"] ?: error("Revocation notification message is null!")
        val messageContext = InboundMessageContext(revocationNotificationMessage, aliceAgent.context.toString())


        val response = faberAgent.revocationNotificationServicev2.processRevocationNotification(messageContext)

        if (!credentialIdValid){
            assertThrows(CredoError::class.java) {
                response
            }
        }

        val wasEventPublished = withTimeoutOrNull(5.seconds) { eventReceived.await() } ?: false
        if (shouldEmit) {
            assertTrue("Expected revocation event was NOT triggered.", wasEventPublished)
        } else {
            assertFalse("Unexpected revocation event was triggered.", wasEventPublished)
        }
    }

    @Test
    @LargeTest
    fun should_emit_revocation_notification_event() = runTest {
        testRevocationEvent(shouldEmit = true, credentialIdValid = true, credentialId = "indy::")
    }

    @Test
    @LargeTest
    fun should_not_emit_revocation_notification_event_because_threadid_is_invalid() = runTest {
        testRevocationEvent(shouldEmit = false, credentialIdValid = false, credentialId = "notIndy::invalidRevRegId::invalidCredRevId")
    }

    /** Revokes a credential */
    private suspend fun revokeCredential(credentialRecord: CredentialExchangeRecord) {
        val didInfo = faberAgent.wallet.publicDid ?: throw Exception("Faber has no public DID.")
        faberAgent.ledgerService.revokeCredential(didInfo, credDefId, 1)
        aliceAgent.eventBus.publish(AgentEvents.RevocationNotificationReceivedEventV2(credentialRecord.copy()))
    }

    /** Prepares ledger for revocation tests */
    private suspend fun prepareForRevocation(): String {
        val didInfo = faberAgent.wallet.publicDid
        val schemaId = faberAgent.ledgerService.registerSchema(
            didInfo!!,
            SchemaTemplate("schema-${UUID.randomUUID()}", "1.0", listOf("name", "age"))
        )
        delay(0.1.seconds)
        val (schema, seqNo) = faberAgent.ledgerService.getSchema(schemaId)
        val credDefId = faberAgent.ledgerService.registerCredentialDefinition(
            didInfo,
            CredentialDefinitionTemplate(schema, "default", true, seqNo)
        )
        faberAgent.ledgerService.registerRevocationRegistryDefinition(
            didInfo,
            RevocationRegistryDefinitionTemplate(credDefId, "default", 100)
        )
        return credDefId
    }
}
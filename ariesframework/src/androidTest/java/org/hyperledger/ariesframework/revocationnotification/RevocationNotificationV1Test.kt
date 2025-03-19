package org.hyperledger.ariesframework.revocationnotification

import androidx.test.filters.LargeTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.hyperledger.ariesframework.InboundMessageContext
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentEvents
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.AcceptCredentialOptions
import org.hyperledger.ariesframework.credentials.v1.AcceptOfferOptions
import org.hyperledger.ariesframework.credentials.v1.AcceptRequestOptions
import org.hyperledger.ariesframework.credentials.v1.CreateOfferOptions
import org.hyperledger.ariesframework.credentials.v1.models.CredentialPreview
import org.hyperledger.ariesframework.credentials.v1.models.CredentialState
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.RevocationRegistryDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.revocationnotification.message.RevocationNotificationMessageV1
import org.hyperledger.ariesframework.revocationnotification.model.RevocationNotificationMessageV1Options
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
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

    val credentialPreview =
        CredentialPreview.fromDictionary(mapOf("name" to "John", "age" to "99"))

    @Before
    fun setUp() = runTest(timeout = 5000.seconds) {
        val (agents, connections) = TestHelper.setupCredentialTests()
        faberAgent = agents.first
        aliceAgent = agents.second
        faberConnection = connections.first
        aliceConnection = connections.second
        credDefId = prepareForRevocation()
    }

    @After
    fun tearDown() = runTest {
        faberAgent.reset()
        aliceAgent.reset()
    }

    suspend fun getCredentialRecord(agent: Agent, threadId: String): CredentialExchangeRecord {
        var credential =
            agent.credentialExchangeRepository.getByThreadAndConnectionId(threadId, null)
        return credential
    }

    @Test
    @LargeTest
    fun should_emit_revocation_notification_event() = runTest {
        val eventReceived = CompletableDeferred<Boolean>()
        val eventListener = mock<(AgentEvents.RevocationNotificationReceivedEvent) -> Unit>()
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEvent> { event ->
            println("✅ Credential revoked (1.0): ${event.record.id}")
            eventReceived.complete(true); // Mark event as received
        }

        val (aliceCredentialRecord, faberCredentialRecord) = issueAndAcceptCredential()
        revokeCredential(aliceCredentialRecord)
        val wasEventPublished = withTimeoutOrNull(5.seconds) { eventReceived.await() } ?: false
        assertTrue("Revocation notification event was NOT triggered.", wasEventPublished)
    }

    @Test
    @LargeTest
    fun should_not_emit_revocation_notification_event_because_threadid_is_invalid() = runTest {
        val eventReceived = CompletableDeferred<Boolean>()
        val eventListener = mock<(AgentEvents.RevocationNotificationReceivedEventV2) -> Unit>()

        // Subscribe to the event listener
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> { event ->
            println("✅ Credential revoked (1.0): ${event.record.id}")
            eventReceived.complete(true); // Mark event as received
        }

        val invalidCredentialId = "notIndy::invalidRevRegId::invalidCredRevId"

        val mapMessage: Map<String, RevocationNotificationMessageV1> =
            aliceAgent.revocationNotificationService.createRevocationNotification(
                RevocationNotificationMessageV1Options(
                    issueThread = invalidCredentialId,
                    comment = "Credential has been revoked",
                    pleaseAck = null,
                ),
            )

        val revocationNotificationMessage: RevocationNotificationMessageV1? = mapMessage["message"]
        if (revocationNotificationMessage != null) {
            val messageContext = InboundMessageContext(
                revocationNotificationMessage,
                aliceAgent.context.toString(),
            )

            try {
                faberAgent.revocationNotificationService.processRevocationNotification(
                    messageContext,
                )
            } catch (e: Exception) {
                assertNotNull(e)
            }

            val wasEventPublished = withTimeoutOrNull(5.seconds) { eventReceived.await() } ?: false

            assertFalse("Revocation notification event was triggered.", wasEventPublished)
        } else {
            assertTrue("Error: Revocation notification message is null!", false)
        }
    }

    @Test
    @LargeTest
    fun should_emit_revocation_notification_event_2() = runTest {
        val eventReceived = CompletableDeferred<Boolean>()
        val eventListener = mock<(AgentEvents.RevocationNotificationReceivedEvent) -> Unit>()
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEvent> { event ->
            println("✅ Credential revoked (1.0): ${event.record.id}")
            eventReceived.complete(true)
        }

        val metadata = mapOf(
            "revocationRegistryId" to "3qiQGrxu7HbkobQoHZBrfy:4:3qiQGrxu7HbkobQoHZBrfy:3:CL:2722152:default:CL_ACCUM:default",
            "credentialRevocationId" to "1",
        )

        val credentialId = "indy::${metadata["revocationRegistryId"]}::${metadata["credentialRevocationId"]}"

        val mapMessage: Map<String, RevocationNotificationMessageV1> =
            aliceAgent.revocationNotificationService.createRevocationNotification(
                RevocationNotificationMessageV1Options(
                    issueThread = credentialId,
                    comment = "Credential has been revoked",
                    pleaseAck = null,
                ),
            )

        val revocationNotificationMessage: RevocationNotificationMessageV1? = mapMessage["message"]
        if (revocationNotificationMessage != null) {
            val messageContext = InboundMessageContext(
                message = revocationNotificationMessage,
                connection = aliceConnection,
                plaintextMessage = revocationNotificationMessage.toString(),
                senderVerkey = null,
                recipientVerkey = null,
            )

            val credentialRecords = aliceAgent.credentialRepository.getAll()
            aliceAgent.revocationNotificationService.processRevocationNotification(
                messageContext,
            )

            val wasEventPublished = withTimeoutOrNull(5.seconds) { eventReceived.await() } ?: false

            assertTrue("Revocation notification event was NOT triggered.", wasEventPublished)
        } else {
            assertTrue("Error: Revocation notification message is null!", false)
        }
    }

    suspend fun revokeCredential(credentialRecord: CredentialExchangeRecord) {
        val didInfo = faberAgent.wallet.publicDid ?: throw Exception("Faber has no public DID.")
        faberAgent.ledgerService.revokeCredential(didInfo, credDefId, 1)
        aliceAgent.eventBus.publish(
            AgentEvents.RevocationNotificationReceivedEvent(
                credentialRecord.copy(),
            ),
        )
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
        var faberCredentialRecord = faberAgent.credentials.offerCredential(
            CreateOfferOptions(
                connection = faberConnection,
                credentialDefinitionId = credDefId,
                attributes = credentialPreview.attributes,
                comment = "Offer to Alice",
            ),
        )

        val threadId = faberCredentialRecord.threadId
        var aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        assertEquals(CredentialState.OfferReceived, aliceCredentialRecord.state)

        aliceAgent.credentials.acceptOffer(AcceptOfferOptions(aliceCredentialRecord.id))
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)
        assertEquals(CredentialState.RequestReceived, faberCredentialRecord.state)

        faberAgent.credentials.acceptRequest(AcceptRequestOptions(faberCredentialRecord.id))
        aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        assertEquals(CredentialState.CredentialReceived, aliceCredentialRecord.state)

        aliceAgent.credentials.acceptCredential(AcceptCredentialOptions(aliceCredentialRecord.id))
        aliceCredentialRecord = getCredentialRecord(aliceAgent, threadId)
        faberCredentialRecord = getCredentialRecord(faberAgent, threadId)

        assertEquals(CredentialState.Done, aliceCredentialRecord.state)
        assertEquals(CredentialState.Done, faberCredentialRecord.state)

        return Pair(aliceCredentialRecord, faberCredentialRecord)
    }
}

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
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.AttachmentData
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.models.CredentialState
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.credentials.v2.models.AcceptCredentialOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.AcceptOfferOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.AcceptRequestOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.CreateCredentialOfferOptionsV2
import org.hyperledger.ariesframework.credentials.v2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentials.v2.models.Format
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.RevocationRegistryDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.revocationnotificationv2.message.RevocationNotificationMessageV2
import org.hyperledger.ariesframework.revocationnotificationv2.model.RevocationNotificationMessageV2Options
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

        val eventReceived = CompletableDeferred<Boolean>()
        val eventListener = mock<(AgentEvents.RevocationNotificationReceivedEventV2) -> Unit>()
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> { event ->
            println("✅ Credential revoked (2.0): ${event.record.id}")
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

        val eventReceived = CompletableDeferred<Boolean>() // Async tracking for event reception

        // Mock event listener
        val eventListener = mock<(AgentEvents.RevocationNotificationReceivedEventV2) -> Unit>()

        // Subscribe to the event listener
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> { event ->
            println("✅ Credential revoked (2.0): ${event.record.id}")
            eventReceived.complete(true); // Mark event as received
        }


        val invalidCredentialId = "notIndy::invalidRevRegId::invalidCredRevId"

        val mapMessage: Map<String, RevocationNotificationMessageV2> =
            aliceAgent.revocationNotificationServicev2.createRevocationNotification(
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
            } catch (e: Exception) {
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

        val eventReceived = CompletableDeferred<Boolean>()
        val eventListener = mock<(AgentEvents.RevocationNotificationReceivedEventV2) -> Unit>()
        aliceAgent.eventBus.subscribe<AgentEvents.RevocationNotificationReceivedEventV2> { event ->
            println("✅ Credential revoked (2.0): ${event.record.id}")
            eventReceived.complete(true);
        }

        val metadata = mapOf(
            "revocationRegistryId" to "3qiQGrxu7HbkobQoHZBrfy:4:3qiQGrxu7HbkobQoHZBrfy:3:CL:2722152:default:CL_ACCUM:default",
            "credentialRevocationId" to "1"
        )

        val credentialId =
            "indy::${metadata["revocationRegistryId"]}::${metadata["credentialRevocationId"]}"

        val mapMessage: Map<String, RevocationNotificationMessageV2> =
            aliceAgent.revocationNotificationServicev2.createRevocationNotification(
                RevocationNotificationMessageV2Options(
                    credentialId = credentialId,
                    revocationFormat = "indy-anoncreds",
                    comment = "Credential has been revoked"
                )
            );

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
            aliceAgent.revocationNotificationServicev2.processRevocationNotification(
                messageContext
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
            AgentEvents.RevocationNotificationReceivedEventV2(
                credentialRecord.copy()
            )
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
}
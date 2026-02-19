package org.hyperledger.ariesframework.agent

import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.models.ConnectionState
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.oob.messages.OutOfBandInvitation
import org.hyperledger.ariesframework.oob.models.HandshakeProtocol
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.net.URL
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AgentTest {
    lateinit var agent: Agent
    private val ip = "192.168.7.241"
    private val mediatorInvitationUrl = "http://" + ip + ":3001/invitation"
    private val agentInvitationUrl = "http://" + ip + ":3002/invitation"
    private val publicMediatorUrl = "https://public.mediator.indiciotech.io?c_i=eyJAdHlwZSI6ICJkaWQ6c292OkJ6Q2JzTlloTXJqSGlxWkRUVUFTSGc7c3BlYy9jb25uZWN0aW9ucy8xLjAvaW52aXRhdGlvbiIsICJAaWQiOiAiMDVlYzM5NDItYTEyOS00YWE3LWEzZDQtYTJmNDgwYzNjZThhIiwgInNlcnZpY2VFbmRwb2ludCI6ICJodHRwczovL3B1YmxpYy5tZWRpYXRvci5pbmRpY2lvdGVjaC5pbyIsICJyZWNpcGllbnRLZXlzIjogWyJDc2dIQVpxSktuWlRmc3h0MmRIR3JjN3U2M3ljeFlEZ25RdEZMeFhpeDIzYiJdLCAibGFiZWwiOiAiSW5kaWNpbyBQdWJsaWMgTWVkaWF0b3IifQ==" // ktlint-disable max-line-length

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    @Test(
        timeout = 600_000,
    )
    @LargeTest
    fun testConnection() = runTest(timeout = 10.minutes) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val url = publicMediatorUrl
        val invitation = ConnectionInvitationMessage.fromUrl(url)

        agent = Agent(context, TestHelper.getBaseConfig())
        agent.initialize()

        var connectionRecord: ConnectionRecord? = null
        agent.eventBus.subscribe<AgentEvents.ConnectionEvent> {
            println("Connection state: ${it.record.state}")
            connectionRecord = it.record
        }
        agent.connections.receiveInvitation(invitation)
        assertEquals(connectionRecord?.state, ConnectionState.Complete)
    }

    /*
      Run a javascript mediator as follows:
        git clone https://github.com/conanoc/aries-framework-javascript.git
        cd aries-framework-javascript
        git checkout demo_kotlin
        cd samples
        AGENT_ENDPOINTS=http://10.0.2.2:3001 npx ts-node mediator.ts
     */
    @Test(timeout = 600_000)
    @LargeTest
    fun testMediatorConnect() = runTest(timeout = 10.minutes) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var config = TestHelper.getBaseConfig()
        val invitationUrl = URL(mediatorInvitationUrl).readText()
        println("Invitation url: $invitationUrl")
        assert(invitationUrl.isNotEmpty())

        config.mediatorConnectionsInvite = invitationUrl
        agent = Agent(context, config)

        agent.eventBus.subscribe<AgentEvents.ConnectionEvent> { println("Connection state changed to ${it.record.state}") }
        agent.eventBus.subscribe<AgentEvents.MediationEvent> { println("Mediation state changed to ${it.record.state}") }

        agent.initialize()
    }

    /*
      Run a javascript mediator as follows:
        AGENT_ENDPOINTS=ws://10.0.2.2:3001 npx ts-node mediator.ts
     */
    @Test(timeout = 600_000)
    @LargeTest
    fun testWebsocketConnect() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var config = TestHelper.getBaseConfig()
        val invitationUrl = URL(mediatorInvitationUrl).readText()
        println("Invitation url: $invitationUrl")
        assert(invitationUrl.isNotEmpty())

        config.mediatorConnectionsInvite = invitationUrl
        agent = Agent(context, config)

        agent.eventBus.subscribe<AgentEvents.ConnectionEvent> { println("Connection state changed to ${it.record.state}") }
        agent.eventBus.subscribe<AgentEvents.MediationEvent> { println("Mediation state changed to ${it.record.state}") }

        agent.initialize()
    }

    @Test(
        timeout = 600_000,
    )
    @LargeTest
    fun testAgentInit() = runTest(timeout = 10.minutes) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var config = TestHelper.getBaseConfig()
        config.mediatorConnectionsInvite = URL(mediatorInvitationUrl).readText()

        // Test init with mediator
        agent = Agent(context, config)
        assertFalse(agent.isInitialized())
        agent.initialize()
        assertTrue(agent.isInitialized())

        // Test init with mediator after shutdown
        agent.shutdown()
        assertFalse(agent.isInitialized())
        agent.initialize()
        assertTrue(agent.isInitialized())
        agent.reset()

        // Test init without mediator
        config.mediatorConnectionsInvite = null
        agent = Agent(context, config)
        assertFalse(agent.isInitialized())
        agent.initialize()
        assertTrue(agent.isInitialized())
        agent.reset()
        assertFalse(agent.isInitialized())
    }

    /*
      Run two javascript mediator as follows:
        AGENT_ENDPOINTS=http://10.0.2.2:3001 npx ts-node mediator.ts
        AGENT_PORT=3002 AGENT_ENDPOINTS=http://10.0.2.2:3002 npx ts-node mediator.ts
     */
    @Ignore
    @Test(timeout = 600_000)
    @LargeTest
    fun testConnectViaMediator() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var config = TestHelper.getBaseConfig()
        config.mediatorConnectionsInvite = URL(mediatorInvitationUrl).readText()
        config.useReturnRoute = false

        agent = Agent(context, config)
        agent.initialize()

        val invitationUrl = URL(agentInvitationUrl).readText()
        val invitation = ConnectionInvitationMessage.fromUrl(invitationUrl)
        agent.connections.receiveInvitation(invitation)
        val result = agent.eventBus.waitFor<AgentEvents.ConnectionEvent> { it.record.state == ConnectionState.Complete }
        assertTrue(result)
    }

    /*
      sudo ifconfig lo0 10.0.2.2 alias
      Run faber in AFJ/demo/ and run mediator in AFJ/samples.
     */
    @Ignore
    @Test(timeout = 600_000)
    @LargeTest
    fun testDemoFaber() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var config = TestHelper.getBcorvinConfig()
        config.preferredHandshakeProtocol = HandshakeProtocol.DidExchange11
        config.mediatorConnectionsInvite = URL(mediatorInvitationUrl).readText()

        agent = Agent(context, config)
        agent.initialize()

        val faberInvitationUrl = "http://192.168.7.241:9001?oob=eyJAdHlwZSI6Imh0dHBzOi8vZGlkY29tbS5vcmcvb3V0LW9mLWJhbmQvMS4xL2ludml0YXRpb24iLCJAaWQiOiI0NzNmNmJkZC1iZTM5LTRmODEtODY3Mi03Yzk5ZjhmMzllYjAiLCJsYWJlbCI6ImZhYmVyIiwiYWNjZXB0IjpbImRpZGNvbW0vYWlwMSIsImRpZGNvbW0vYWlwMjtlbnY9cmZjMTkiXSwiaGFuZHNoYWtlX3Byb3RvY29scyI6WyJodHRwczovL2RpZGNvbW0ub3JnL2RpZGV4Y2hhbmdlLzEuMSIsImh0dHBzOi8vZGlkY29tbS5vcmcvY29ubmVjdGlvbnMvMS4wIl0sInNlcnZpY2VzIjpbeyJpZCI6IiNpbmxpbmUtMCIsInNlcnZpY2VFbmRwb2ludCI6Imh0dHA6Ly8xOTIuMTY4LjcuMjQxOjkwMDEiLCJ0eXBlIjoiZGlkLWNvbW11bmljYXRpb24iLCJyZWNpcGllbnRLZXlzIjpbImRpZDprZXk6ejZNa3ZpSm91VHN2MmpDeEM5Y3U3NGcyaWptd2hMMkpvN2UxWHhIWHlFS1pTOXZ5Il0sInJvdXRpbmdLZXlzIjpbXX1dfQ" // ktlint-disable max-line-length
        val invitation = OutOfBandInvitation.fromUrl(faberInvitationUrl)
        agent.oob.receiveInvitation(invitation)

        agent.eventBus.subscribe<AgentEvents.CredentialEvent> {
            println("Credential State: ${it.record.state}")
        }
        agent.eventBus.subscribe<AgentEvents.ProofEvent> {
            println("Proof State: ${it.record.state}")
        }

        delay(120.seconds)
    }

    // For two agents behind mediators to connect, message forward is needed.
    @Test(
        timeout = 600_000,
    )
    @LargeTest
    fun testMessageForward() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val aliceConfig = TestHelper.getBaseConfig("alice")
        aliceConfig.mediatorPickupStrategy = MediatorPickupStrategy.Implicit
        aliceConfig.mediatorConnectionsInvite = publicMediatorUrl
        aliceConfig.mediatorPollingInterval = 1

        val alice = Agent(context, aliceConfig)
        agent = alice
        alice.initialize()

        val faberConfig = TestHelper.getBaseConfig("faber")
        faberConfig.mediatorPickupStrategy = MediatorPickupStrategy.Implicit
        faberConfig.mediatorConnectionsInvite = publicMediatorUrl
        faberConfig.mediatorPollingInterval = 1

        val faber = Agent(context, faberConfig)
        faber.initialize()

        val (aliceConnection, faberConnection) = TestHelper.makeConnection(alice, faber, 3.seconds)
        assertEquals(aliceConnection.state, ConnectionState.Complete)
        assertEquals(faberConnection.state, ConnectionState.Complete)

        // alice will be reset on tearDown
        faber.reset()
    }
}

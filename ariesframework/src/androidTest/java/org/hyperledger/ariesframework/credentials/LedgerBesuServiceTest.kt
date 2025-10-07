package org.hyperledger.ariesframework.credentials

import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LedgerBesuServiceTest {
    lateinit var agent: Agent

    /*
     Run a besu as follows:
       git clone https://github.com/guilherme-funchal/indy-besu.git
       cd aries-framework-javascriptindy-besu
       git checkout Revocation_SERPRO
       ./network/scripts/run.sh

     Generate data test:
       cd vdr/wrappers/python
       python3 -m pip install --upgrade build
       python3 -m build
       pip3 install eth_keys
       python3 -m demo.test
     */

    @Before
    fun setUp() = runTest(timeout = 30.seconds) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TestHelper.getBesuBaseConfig("faber", true)
        agent = Agent(context, config)
        agent.initialize()
    }

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    /*
    Adjust the data according to those generated in python demo
     */
    @Test @LargeTest
    fun testBesuSchema() = runTest(timeout = 10.minutes) {
        agent.ledgerService.getSchema("did:ethr:0xce70ce892768d46caf120b600dec29ed20198982/anoncreds/v0/SCHEMA/WZXL9B/1.0.0")
    }

    /*
    Adjust the data according to those generated in python demo
     */
    @Test @LargeTest
    fun testBesuCredential() = runTest(timeout = 10.minutes) {
        agent.ledgerService.getCredentialDefinition("did:ethr:0xce70ce892768d46caf120b600dec29ed20198982/anoncreds/v0/CLAIM_DEF/did:ethr:0xce70ce892768d46caf120b600dec29ed20198982:WZXL9B:1.0.0/cred_def_tag")
    }

    /*
    Adjust the data according to those generated in python demo
     */
    @Test @LargeTest
    fun testBesuRevocation() = runTest(timeout = 10.minutes) {
        agent.ledgerService.getRevocationRegistryDefinition("did:ethr:0xce70ce892768d46caf120b600dec29ed20198982/anoncreds/v0/REV_REG_DEF/did:ethr:0xce70ce892768d46caf120b600dec29ed20198982:WZXL9B:1.0.0/cred_def_tag/rev_reg_def_tag")
        val timestamp: Int = Instant.now().epochSecond.toInt()
        agent.ledgerService.getRevocationRegistry("did:ethr:0xce70ce892768d46caf120b600dec29ed20198982/anoncreds/v0/REV_REG_DEF/did:ethr:0xce70ce892768d46caf120b600dec29ed20198982:WZXL9B:1.0.0/cred_def_tag/rev_reg_def_tag", timestamp)
        agent.ledgerService.getRevocationRegistryDelta("did:ethr:0xce70ce892768d46caf120b600dec29ed20198982/anoncreds/v0/REV_REG_DEF/did:ethr:0xce70ce892768d46caf120b600dec29ed20198982:WZXL9B:1.0.0/cred_def_tag/rev_reg_def_tag", timestamp, timestamp)
    }
}

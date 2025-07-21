package org.hyperledger.ariesproject

import android.app.Application
import android.content.res.Configuration
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.BesuLedgerConfig
import org.hyperledger.ariesframework.agent.MediatorPickupStrategy
import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import java.io.File

const val PREFERENCE_NAME = "aries-framework-kotlin-sample"
const val genesisPath = "von.txn"

class WalletApp : Application() {
    lateinit var agent: Agent
    var walletOpened: Boolean = false

    private fun copyResourceFile(resource: String) {
        val inputStream = applicationContext.assets.open(resource)
        val file = File(applicationContext.filesDir.absolutePath, resource)
        file.outputStream().use { inputStream.copyTo(it) }
    }

    private suspend fun openWallet() {
        val pref = applicationContext.getSharedPreferences(PREFERENCE_NAME, 0)
        var key = pref.getString("walletKey", null)

        if (key == null) {
            key = Agent.generateWalletKey()
            pref.edit().putString("walletKey", key).apply()
        }
        copyResourceFile(genesisPath)
        val properties = ConfigLoader.loadProperties(this)
        val invitationUrl = properties.getProperty("invitation_url")

        val androidId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // 2) Monte o label, por ex. "SimpleApp-<ANDROID_ID>"
        val agentLabel = "SimpleApp-1X$androidId"

        val besuLedgerContig = BesuLedgerConfig(
            chainId= 1337u,
            nodeAddress= "http://10.139.76.166:8000",
        )
        val config = AgentConfig(
            walletKey = key,
            genesisPath = File(applicationContext.filesDir.absolutePath, genesisPath).absolutePath,
            mediatorConnectionsInvite = invitationUrl,
            mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
            label = agentLabel,
            autoAcceptCredential = AutoAcceptCredential.Never,
            autoAcceptProof = AutoAcceptProof.Never,
            useLedgerService = false, // indy
            useBesuLedger =  true, //besu
            besuLedgerConfig = besuLedgerContig,
        )
        agent = Agent(applicationContext, config)
        agent.initialize()

        walletOpened = true
        Log.d("demo", "Agent initialized")
    }

    override fun onCreate() {
        super.onCreate()
        GlobalScope.launch(Dispatchers.IO) {
            openWallet()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }
}

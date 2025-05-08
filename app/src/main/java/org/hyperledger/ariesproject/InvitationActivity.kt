package org.hyperledger.ariesproject

import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.messages.TrustPingMessage
import org.hyperledger.ariesframework.oob.models.CreateOutOfBandInvitationConfig

class InvitationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_invitation)

        if (savedInstanceState == null) {
            // Chama a Coroutine para gerar a invitation
            lifecycleScope.launch {
                val invitationUrl = generateInvitation()

                val qrCodeFragment = InvitationFragment()
                val args = Bundle()
                args.putString("url", invitationUrl)
                qrCodeFragment.arguments = args

                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, qrCodeFragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit()
            }
        }
    }

    private suspend fun generateInvitation(): String {
        val androidId = Settings.Secure.getString(
            applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        // 2) Monte o label, por ex. "SimpleApp-<ANDROID_ID>"
        val agentLabel = "SimpleApp-$androidId"
        val config = CreateOutOfBandInvitationConfig(
            label = agentLabel,
            handshake = true,
        )
        val app = application as WalletApp
        val endpoint =  "https://blockchain.cpqd.com.br/cpqdid/agent-mediator-endpoint-com" //app.agent.agentConfig.endpoints.get(0);
        val outOfBandRecord = app.agent.oob.createInvitation(config)
        val invitation: String = outOfBandRecord.outOfBandInvitation.toUrl(endpoint)
        Log.e("URL", invitation);
        return invitation
    }
}

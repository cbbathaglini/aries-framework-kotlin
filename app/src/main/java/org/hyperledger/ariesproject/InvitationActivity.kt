package org.hyperledger.ariesproject

import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.FrameLayout
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.hyperledger.ariesframework.connection.messages.ConnectionInvitationMessage
import org.hyperledger.ariesframework.connection.messages.TrustPingMessage
import org.hyperledger.ariesframework.oob.models.CreateOutOfBandInvitationConfig
import org.hyperledger.ariesproject.databinding.ActivityInvitationBinding

class InvitationActivity : BaseActivity() {

    private lateinit var binding: ActivityInvitationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInvitationBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar).setNavigationOnClickListener {
            finish()
        }

        if (savedInstanceState == null) {
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

        val agentLabel = "SimpleApp-$androidId"
        val config = CreateOutOfBandInvitationConfig(
            label = agentLabel,
            handshake = true,
        )
        val app = application as WalletApp
        val properties = ConfigLoader.loadProperties(this)
        val endpoint =  properties.getProperty("endpoint")
        val outOfBandRecord = app.agent.oob.createInvitation(config)
        val invitation: String = outOfBandRecord.outOfBandInvitation.toUrl(endpoint)
        Log.e("URL", invitation);
        return invitation
    }
}

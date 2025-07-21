package org.hyperledger.ariesproject

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hyperledger.ariesproject.databinding.ActivityCredentialDetailBinding
import org.hyperledger.ariesproject.databinding.CredentialDetailBinding
import anoncreds_uniffi.Credential
import anoncreds_uniffi.W3cCredential
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.anoncreds.storage.CredentialRecord
import org.hyperledger.ariesproject.databinding.ActivityCredentialW3cDetailBinding
import org.hyperledger.ariesproject.databinding.CredentialW3cDetailBinding


class CredentialW3cDetailFragment : Fragment() {

    //private var item: W3cCredential? = null
    private var item: String? = null
    private var credentialId: String? = null
    private lateinit var detailBinding: ActivityCredentialW3cDetailBinding
    private lateinit var binding: CredentialW3cDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            if(it.containsKey(ARG_CREDENTIAL_W3C) && it.getString(ARG_CREDENTIAL_W3C) != null){
                Log.i("W3C", "it: ${it.getString(ARG_CREDENTIAL_W3C)}")

                item = it.getString(ARG_CREDENTIAL_W3C)
                //item = it.getString(ARG_CREDENTIAL_W3C)
                //Log.i("W3C", "w3cCredential: ${w3cCredential.toString()}")
                credentialId = it.getString(ARG_CREDENTIAL_W3C_ID)
                Log.i("W3C", "credentialId: ${credentialId}")
            }

//            detailBinding = ActivityCredentialDetailBinding.inflate(layoutInflater)
//            detailBinding.toolbarLayout.title = getString(R.string.title_credential_detail)

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = CredentialW3cDetailBinding.inflate(inflater, container, false)
        val rootView = binding.root
        binding.credentialW3cDetail.text =  item

//            if (item != null) {
//            item?.let {
//                val attrs = it.values()
//                binding.credentialDetail.text = attrs.map { attr ->
//                    "${attr.key}: ${attr.value}"
//                }.joinToString("\n")
//
//                val activity = activity as CredentialDetailActivity
//                val app = activity.application as WalletApp
          //  }

        return rootView
    }

    companion object {
        const val ARG_CREDENTIAL_W3C = "item_credential_W3C"
        const val ARG_CREDENTIAL_W3C_ID = "item_credential_id_W3C"
    }
}

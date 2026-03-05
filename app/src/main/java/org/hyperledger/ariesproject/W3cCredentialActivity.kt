package org.hyperledger.ariesproject

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NavUtils
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import anoncreds_uniffi.CredentialConversions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.hyperledger.ariesframework.util.LogUtil
import org.hyperledger.ariesframework.vc.model.W3cCredential
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesproject.databinding.ActivityW3cCredentialBinding

class W3cCredentialActivity : BaseActivity() {

    private lateinit var binding: ActivityW3cCredentialBinding
    private val json = Json { ignoreUnknownKeys = true }
    private val adapter = W3cCredentialAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityW3cCredentialBinding.inflate(layoutInflater)
        findViewById<FrameLayout>(R.id.baseContainer).addView(binding.root)

        binding.credentialsRecycler.layoutManager = LinearLayoutManager(this)
        binding.credentialsRecycler.adapter = adapter

        // Toolbar + Home (back)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = "W3C Credentials"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val app = application as WalletApp

        binding.saveButton.setOnClickListener {
            val vcJson = binding.vcEditText.text?.toString()?.trim().orEmpty()
            if (vcJson.isBlank()) return@setOnClickListener showAlert("Cole o JSON da credencial.")

            lifecycleScope.launch(Dispatchers.IO) {
                try {

                    val record : W3cCredential = app.agent.w3cCredentialService.processAndStorew3cCredential(vcJson)

                    val parsed = json.parseToJsonElement(vcJson).jsonObject
                    val normalized = W3cCredential.normalizeIncomingW3cPayload(parsed)

                    val credentialW3cStr = CredentialConversions().credentialFromW3cJson(
                        parsed["credential"].toString()
                    )

                    LogUtil.info(this) {"credentialW3cStr: $credentialW3cStr"}

                    //val w3cCredential = W3cCredential(credentialW3cStr)


                    LogUtil.info(this@W3cCredentialActivity) { "Saving VC id=${record.id}" }

                    withContext(Dispatchers.Main) {
                        binding.resultText.text =
                            "Salvo com sucesso.\n(id detectado: ${record.id ?: "N/A"})"
                    }
                } catch (e: Exception) {
                    LogUtil.error(this@W3cCredentialActivity, e) { "Failed saving VC" }
                    withContext(Dispatchers.Main) {
                        showAlert("Erro ao salvar: ${e.localizedMessage}")
                    }
                }
            }
        }

        binding.listButton.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val repo = app.agent.w3cCredentialRepository
                    val all = repo.getAll()

                    withContext(Dispatchers.Main) {
                        adapter.submit(all)
                        binding.resultText.text = "Total: ${all.size}"
                    }

                } catch (e: Exception) {
                    LogUtil.error(this@W3cCredentialActivity, e) { "Failed listing VCs" }
                    withContext(Dispatchers.Main) {
                        showAlert("Erro ao listar: ${e.localizedMessage}")
                    }
                }
            }
        }

        binding.getButton.setOnClickListener {
            val id = binding.idEditText.text?.toString()?.trim().orEmpty()
            if (id.isBlank()) return@setOnClickListener showAlert("Digite um id para buscar.")

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val results = app.agent.w3cCredentialService
                        .findByCredentialSubjectId(id)

                    withContext(Dispatchers.Main) {
                        if (results.isEmpty()) {
                            adapter.submit(emptyList())
                            binding.resultText.text = "Nada encontrado para subjectId=$id"
                        } else {
                            adapter.submit(results)
                            binding.resultText.text = "Encontradas: ${results.size}"
                        }
                    }
                } catch (e: Exception) {
                    LogUtil.error(this@W3cCredentialActivity, e) { "Failed get VC by id=$id" }
                    withContext(Dispatchers.Main) {
                        showAlert("Erro ao buscar: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                NavUtils.navigateUpFromSameTask(this) // ou finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun showAlert(message: String) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
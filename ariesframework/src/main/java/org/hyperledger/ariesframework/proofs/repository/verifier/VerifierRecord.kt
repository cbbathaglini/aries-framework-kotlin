package org.hyperledger.ariesframework.proofs.repository.verifier

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.proofs.v2.messages.PresentationMessageV2
import org.hyperledger.ariesframework.proofs.v2.messages.RequestPresentationMessageV2
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.Tags

@Serializable
data class PresentationVerifier(
    var presentationMessage: PresentationMessageV2? = null,
    var isVerified: Boolean? = false,
    var isOffline: Boolean? = true,
    var proofRecordId: String? = null
)

@Serializable
class VerifierRecord(
    // Campos serializáveis normais
    var globalThreadId: String? = null,
    var proofRequest: AnonCredsProofRequest? = null,
    var requestMessage: RequestPresentationMessageV2? = null,
    var presentation: MutableList<PresentationVerifier>? = mutableListOf(),

    // 🔹 Campos herdados do BaseRecord — marcados como @Transient (não serializados)
    @Transient override var id: String = generateId(),
    @Transient override var _tags: Tags? = null,
    @Transient override val createdAt: Instant = Clock.System.now(),
    @Transient override var updatedAt: Instant? = null
) : BaseRecord() {

    override fun getTags(): Tags {
        val tags = (_tags ?: mutableMapOf()).toMutableMap()
        globalThreadId?.let { tags["globalThreadId"] = it }
        return tags
    }

    fun addPresentation(newPresentation: PresentationVerifier) {
        if (presentation == null) presentation = mutableListOf()
        presentation?.add(newPresentation)
    }

    fun printDetails() {
        println("VerifierRecord Details:")
        println("ID: $id")
        println("Created At: $createdAt")
        println("Updated At: $updatedAt")
        println("Global Thread ID: $globalThreadId")
        println("Tags: $_tags")

        println("Proof Request: ${proofRequest ?: "null"}")
        println("Request Message: ${requestMessage ?: "null"}")

        if (!presentation.isNullOrEmpty()) {
            presentation?.forEachIndexed { index, pres ->
                println("Presentation #${index + 1}: ${pres.proofRecordId}, verified=${pres.isVerified}")
            }
        } else {
            println("Presentations: null or empty")
        }
    }

    companion object {
        const val type = "VerifierRecord"
    }
}
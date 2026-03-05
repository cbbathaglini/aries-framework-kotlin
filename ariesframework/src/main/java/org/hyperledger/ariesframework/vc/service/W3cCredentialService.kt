package org.hyperledger.ariesframework.vc.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.hyperledger.ariesframework.vc.dataintegrity.W3cJsonLdCredentialService
import org.hyperledger.ariesframework.vc.model.W3cCredential
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRepository

class W3cCredentialService(
    private val w3cCredentialRepository: W3cCredentialRepository,
    private val w3cJsonLdCredentialService: W3cJsonLdCredentialService,
    private val w3cJwtCredentialService: W3cJwtCredentialService,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Writes a credential to storage
     *
     * @param record the credential to be stored
     * @returns the credential record that was written to storage
     */
    suspend fun storeCredentialW3cJsonLdVerifiableCredential(jsonLdVerifiableCredential: W3cJsonLdVerifiableCredential): W3cCredentialRecord {
        val expandedTypes: Map<String, List<String>> = w3cJsonLdCredentialService.getExpandedTypesForCredential(
            jsonLdVerifiableCredential.context,
            jsonLdVerifiableCredential.type,
        )
        // PrintLongLine.print("verifiable: $jsonLdVerifiableCredential")

//        val expandedTypes: Map<String, String> = mapOf("type" to "https://www.w3.org/2018/credentials#VerifiableCredential")
//        logger.info("expandedTypes: ${expandedTypes.toString()}")

        val jsonForProof = Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            // se você já tem um serializersModule global, melhor ainda:
            // serializersModule = agent.serializersModule
        }

        val proofElements: List<JsonElement>? =
            jsonLdVerifiableCredential.proofs
                ?.map { proof -> jsonForProof.encodeToJsonElement(proof) }

        val w3cCredential = W3cCredential(
            context = jsonLdVerifiableCredential.context,
            id = jsonLdVerifiableCredential.id,
            type = jsonLdVerifiableCredential.type,
            issuer = jsonLdVerifiableCredential.issuer,
            issuanceDate = jsonLdVerifiableCredential.issuanceDate,
            credentialSubject = jsonLdVerifiableCredential.credentialSubject,
            expirationDate = jsonLdVerifiableCredential.expirationDate,
            credentialSchema = jsonLdVerifiableCredential.credentialSchema,
            credentialStatus = jsonLdVerifiableCredential.credentialStatus,
            proofs = proofElements,
        )

        val expandedTypesStr: Map<String, String> = expandedTypes.mapValues { (_, v) -> v.joinToString(",") }
        val w3cCredentialRecord = W3cCredentialRecord(
            tags = expandedTypesStr,
            credential = w3cCredential,
        )

        // logger.info("w3cCredentialRecord =====> $w3cCredentialRecord")
        w3cCredentialRepository.save(w3cCredentialRecord)
        return w3cCredentialRecord
    }

    suspend fun storeCredentialW3cCredential(w3cCredential: W3cCredential): W3cCredentialRecord {
        val expandedTypes: Map<String, List<String>> = w3cJsonLdCredentialService.getExpandedTypesForCredential(w3cCredential.context, w3cCredential.type)

        val expandedTypesStr: Map<String, String> = expandedTypes.mapValues { (_, v) -> v.joinToString(",") }
        val w3cCredentialRecord = W3cCredentialRecord(
            tags = expandedTypesStr,
            credential = w3cCredential,
        )
        w3cCredentialRepository.save(w3cCredentialRecord)
        return w3cCredentialRecord
    }

    suspend fun processAndStorew3cCredential(rawJson: String): W3cCredential {
        val parsed = json.parseToJsonElement(rawJson).jsonObject
        val normalized = W3cCredential.normalizeIncomingW3cPayload(parsed)

        val credential = json.decodeFromJsonElement(
            W3cCredential.serializer(),
            normalized,
        )

        storeCredentialW3cCredential(credential)
        return credential
    }

    suspend fun findByCredentialSubjectId(subjectId: String): List<W3cCredentialRecord> {
        return w3cCredentialRepository.findByCredentialSubjectId(subjectId)
    }
}

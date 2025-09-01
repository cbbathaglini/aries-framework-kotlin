package org.hyperledger.ariesframework.anoncreds.service

import anoncreds_uniffi.Credential
import anoncreds_uniffi.CredentialConversions
import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.CredentialOffer
import anoncreds_uniffi.CredentialRequestMetadata
import anoncreds_uniffi.CredentialRequestTuple
import anoncreds_uniffi.Prover
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.Verifier
import anoncreds_uniffi.W3cCredential
import anoncreds_uniffi.W3cProcess
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.Gson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequestMetadata
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.LegacyToW3cCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.ProcessOptions
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialW3cOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateCredentialRequestReturn
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateLinkSecretOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateLinkSecretReturn
import org.hyperledger.ariesframework.anoncreds.model.holder.StoreLinkSecretOptions
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsCredentialRecord
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsLinkSecretRecord
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers
import org.hyperledger.ariesframework.credentials.formats.anoncreds.MetadataKeys
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.storage.BaseRecord
import org.hyperledger.ariesframework.util.Base58
import org.hyperledger.ariesframework.util.PrintLongLine
import org.hyperledger.ariesframework.vc.model.ProcessCredentialOptions
import org.hyperledger.ariesframework.vc.model.W3cAnonCredsCredentialMetadata
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesframework.vc.util.W3cAnonCredsUtils
import org.slf4j.LoggerFactory

class AnonCredsRsHolderService(val agent: Agent) : AnonCredsHolderService {

    private val logger = LoggerFactory.getLogger(AnonCredsRsHolderService::class.java)

    override suspend fun storeCredential(
        options: StoreCredentialOptions,
        metadata: Map<String, Any>?,
    ): String {
        val credential = options.credential
        val credentialDefinition = options.credentialDefinition
        val credentialDefinitionId = options.credentialDefinitionId
        val credentialRequestMetadata = options.credentialRequestMetadata
        val schema = options.schema
        val revocationRegistry = options.revocationRegistry

        logger.info("credential: $credential")
        logger.info("credentialDefinition: $credentialDefinition")
        logger.info("credentialRequestMetadata: $credentialRequestMetadata")
        logger.info("revocationRegistry: $revocationRegistry")

        var w3cJsonLdCredential: W3cJsonLdVerifiableCredential
        if (credential is W3cJsonLdVerifiableCredential) {
            w3cJsonLdCredential = credential
            logger.info("w3cJsonLdCredential: $w3cJsonLdCredential")
        } else {
            val legacyToW3cCredentialOptions = LegacyToW3cCredentialOptions(
                credential = credential as AnonCredsCredential,
                issuerId = credentialDefinition.issuerId,
                processOptions = ProcessOptions(
                    credentialDefinition = credentialDefinition,
                    credentialRequestMetadata = credentialRequestMetadata,
                    revocationRegistryDefinition = revocationRegistry?.definition,
                ),
            )
            logger.info("legacyToW3cCredentialOptions: $legacyToW3cCredentialOptions")

            w3cJsonLdCredential = legacyToW3cCredential(legacyToW3cCredentialOptions)
            logger.info("w3cJsonLdCredential: $w3cJsonLdCredential")
        }

        val storeCredentialW3cOptions = StoreCredentialW3cOptions(
            credential = w3cJsonLdCredential,
            credentialDefinitionId = credentialDefinitionId,
            schema = schema,
            credentialDefinition = credentialDefinition,
            revocationRegistryDefinition = revocationRegistry?.definition,
            revocationRegistryId = revocationRegistry?.id,
            credentialRequestMetadata = credentialRequestMetadata,
        )
        val w3cCredentialRecord = storeW3cCredential(storeCredentialW3cOptions)
        logger.info("w3cCredentialRecord: $w3cCredentialRecord")

        return w3cCredentialRecord.id
    }

    override suspend fun legacyToW3cCredential(options: LegacyToW3cCredentialOptions): W3cJsonLdVerifiableCredential {
        val anonCredsCredential: AnonCredsCredential = options.credential
        val issuerId: String = options.issuerId
        val processOptions: ProcessOptions? = options.processOptions

        logger.info("anonCredsCredential: $anonCredsCredential")

        // val anonCredsCredentialJson = Gson().toJson(anonCredsCredential)

        val anonCredsCredentialJson = Json.encodeToString(anonCredsCredential)
        PrintLongLine.print(anonCredsCredentialJson)

        var credential: Credential = Credential(anonCredsCredentialJson)
        logger.info("credential: $credential")

        val credentialW3cStr: String = CredentialConversions().credentialToW3cJson(credential, issuerId, "1.1")
        logger.info("credentialW3cStr: $credentialW3cStr")

        val w3cCredential: W3cCredential = W3cCredential(credentialW3cStr)

        val w3cJsonLdVerifiableCredential: W3cJsonLdVerifiableCredential =
            convertToW3cJsonLd(w3cCredential, credentialW3cStr)

        var w3cJsonLdVC = w3cJsonLdVerifiableCredential
        if (processOptions != null) {
            logger.info("processOptions: $processOptions")
            w3cJsonLdVC = processW3cCredential(w3cCredential, w3cJsonLdVerifiableCredential, processOptions)
        }

        // logger.info("aaaaaaaaa======== ${agent.w3cCredentialRepository.getAll().toString()}")

//        } finally {
//            anonCredsCredential?.handle?.clear()
//            w3cCredentialObj?.handle?.clear()
//        }

        return w3cJsonLdVC
    }

    private fun convertToW3cJsonLd(
        w3cCredential: W3cCredential,
        credentialW3cStr: String,
    ): W3cJsonLdVerifiableCredential {
        logger.info("w3cCredential: ${w3cCredential.toJson()}")

        val element = Json.parseToJsonElement(w3cCredential.toJson()).jsonObject.toMutableMap()
        logger.info("element: $element")

        val subject = element["credentialSubject"]
        if (subject != null && subject !is JsonArray) {
            element["credentialSubject"] = JsonArray(listOf(subject))
        }

        val typeCred = element["type"]
        if (typeCred != null && typeCred !is JsonArray) {
            element["type"] = JsonArray(listOf(typeCred))
        }

        val normalized = JsonObject(element)
        logger.info("normalized: $normalized")

        val w3cJsonLdVerifiableCredential: W3cJsonLdVerifiableCredential = W3cJsonLdVerifiableCredential.fromJson(normalized.toString())
        // val  w3cJsonLdVerifiableCredential : W3cJsonLdVerifiableCredential = Gson().fromJson(normalized, W3cJsonLdVerifiableCredential::class.java)
        logger.info("w3cJsonLdVerifiableCredential: $w3cJsonLdVerifiableCredential")

        return w3cJsonLdVerifiableCredential
    }

    private suspend fun processW3cCredential(w3cCredential: W3cCredential, crew3cJsonLdVC: W3cJsonLdVerifiableCredential, processOptions: ProcessOptions): W3cJsonLdVerifiableCredential {
        val mapper: ObjectMapper = jacksonObjectMapper()
        val (credentialDefinition, credentialRequestMetadata, revocationRegistryDefinition) = processOptions

        val processCredentialOptions = ProcessCredentialOptions(
            credentialRequestMetadata = credentialRequestMetadata,
            linkSecret = agent.wallet.linkSecretId!!,
            revocationRegistryDefinition = revocationRegistryDefinition,
            credentialDefinition = credentialDefinition,
        )
        logger.info("credentialRequestMetadata: $credentialRequestMetadata ")
        logger.info("processCredentialOptions: $processCredentialOptions ")
        logger.info("revocationRegistryDefinition: ${processCredentialOptions.revocationRegistryDefinition} ")
        val credentialDefinitionJson: String = processCredentialOptions.credentialDefinition.toJson() ?: throw CredoError("credentialRequestMetadata not found")
        logger.info("credentialDefinitionJson: $credentialDefinitionJson ")

        val credentialDefinitionUniffi: CredentialDefinition = CredentialDefinition(credentialDefinitionJson)
        logger.info("credentialDefinitionUniffi: ${credentialDefinitionUniffi.toJson()} ")

        val jsonString = Gson().toJson(credentialRequestMetadata)
        val cleaned = jsonString.replace("\\\"", "")
        logger.info("jsonString: $cleaned")

        val credentialRequestMetadataUniffi: CredentialRequestMetadata = CredentialRequestMetadata(cleaned)
        logger.info("credentialRequestMetadataUniffi: ${credentialRequestMetadataUniffi.toJson()} ")

        var revocationRegistryDefinitionUniffi: RevocationRegistryDefinition? = null
        if (revocationRegistryDefinition != null) {
            logger.info("seloko")
            val anonCredsRevocationRegistryDefinitionJson = Gson().toJson(revocationRegistryDefinition) ?: throw CredoError("revocationRegistryDefinition not found")
            logger.info("anonCredsRevocationRegistryDefinitionJson: $anonCredsRevocationRegistryDefinitionJson")
            revocationRegistryDefinitionUniffi = RevocationRegistryDefinition(anonCredsRevocationRegistryDefinitionJson)
            logger.info("revocationRegistryDefinitionUniffi: $revocationRegistryDefinitionUniffi ")
        }
        logger.info("w3cCredential: ${w3cCredential.toJson()} ")
        logger.info("credReqMetadata: ${credentialRequestMetadataUniffi.toJson()} ")
        logger.info("linkSecret: ${processCredentialOptions.linkSecret} ")
        logger.info("credDef: ${credentialDefinitionUniffi.toJson()} ")
        logger.info("revRegDef: $revocationRegistryDefinitionUniffi ")

        val linkSecret = agent.anoncredsService.getLinkSecret(processCredentialOptions.linkSecret)
        logger.info("linkSecret: $linkSecret ")

        val processedW3cCredential = W3cProcess().processCredential(
            cred = w3cCredential,
            credReqMetadata = credentialRequestMetadataUniffi,
            linkSecret = linkSecret,
            credDef = credentialDefinitionUniffi,
            revRegDef = revocationRegistryDefinitionUniffi,
        )
        logger.info("processedW3cCredential: ${processedW3cCredential.toJson()} ")

        return convertToW3cJsonLd(processedW3cCredential, processedW3cCredential.toJson())
    }

    private suspend fun storeW3cCredential(options: StoreCredentialW3cOptions): W3cCredentialRecord {
        val credential: W3cJsonLdVerifiableCredential = options.credential
        val credentialDefinitionId: String = options.credentialDefinitionId
        val schema: AnonCredsSchema = options.schema
        val credentialDefinition: AnonCredsCredentialDefinition = options.credentialDefinition
        // val revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition? = options.revocationRegistryDefinition
        val revocationRegistryId: String? = options.revocationRegistryId
        val credentialRequestMetadata: AnonCredsCredentialRequestMetadata = options.credentialRequestMetadata

        logger.info("credential: $credential ")
        val issuer = credential.issuer.toString()
        logger.info("issuer: $issuer ")

        val w3cJsonLdVerifiableCredentialStr = Json.encodeToString(credential)
        var w3cJsonLdVerifiableCredentialStrClean = w3cJsonLdVerifiableCredentialStr.replace("\\\"", "")
        w3cJsonLdVerifiableCredentialStrClean = Regex("\"credentialSubject\"\\s*:\\s*\\[(\\{.*?\\})\\]")
            .replace(w3cJsonLdVerifiableCredentialStrClean) { matchResult ->
                val inner = matchResult.groupValues[1]
                "\"credentialSubject\": $inner"
            }

        logger.info("w3cJsonLdVerifiableCredentialStr: $w3cJsonLdVerifiableCredentialStrClean ")

        val credentialUniffi: Credential = CredentialConversions().credentialFromW3cJson(w3cJsonLdVerifiableCredentialStrClean)
        logger.info("credentialUniffi: ${credentialUniffi.toJson()} ")

        val credentialW3cStr: String = CredentialConversions().credentialToW3cJson(credentialUniffi, "did:sov:" + issuer, "1.1")
        logger.info("credentialW3cStr: $credentialW3cStr ")

        val w3cCredential: W3cCredential = W3cCredential(credentialW3cStr)
        logger.info("credentialUniffi: ${credentialUniffi.toJson()} ")

//        //var anonCredsCredential: Credential = CredentialConversions().credentialFromW3cJson(credentialJson)
//        val w3cAnonCredsCredential = W3cCredential.fromJson(credentialJson) //[TODO] uniffi another version with w3c

        if (credential.credentialSubject.size > 1) {
            throw CredoError("Credential subject must be an object, not an array.")
        }

//        val methodName = agent.anonCredsRegistryService
//            .getRegistryForIdentifier(issuer).methodName
        val tags = W3cAnonCredsUtils.getW3cRecordAnonCredsTags(
            credentialSubject = credential.credentialSubject.first(),
            issuerId = issuer,
            schemaId = credentialDefinition.schemaId,
            schema = schema,
            credentialDefinitionId = credentialDefinitionId,
            revocationRegistryId = revocationRegistryId,
            credentialRevocationId = credentialUniffi.revRegIndex()?.toString(), // w3ccredential revRegIndex
            linkSecretId = credentialRequestMetadata.link_secret_name,
            methodName = "ethr",
        )
        logger.info("tags: $tags ")

        val w3cCredentialRecord = agent.w3cCredentialService.storeCredentialW3cJsonLdVerifiableCredential(credential)
        logger.info("w3cCredentialRecord => $w3cCredentialRecord ")

        val anonCredsCredentialMetadata: W3cAnonCredsCredentialMetadata = W3cAnonCredsCredentialMetadata(
            credentialRevocationId = tags["anonCredsCredentialRevocationId"],
            linkSecretId = tags["anonCredsLinkSecretId"]!!.trim('"'),
            methodName = tags["anonCredsMethodName"]!!,
        )

        w3cCredentialRecord.setTags(tags)

        val anonCredsCredentialMetadataJson = Json.encodeToJsonElement(anonCredsCredentialMetadata)
        logger.info("anonCredsCredentialMetadataJson ========> $anonCredsCredentialMetadataJson ")
        w3cCredentialRecord.metadata.set(MetadataKeys.W3cAnonCredsCredentialMetadataKey, anonCredsCredentialMetadataJson)

        logger.info("w3cCredentialRecord ========> $w3cCredentialRecord ")
        agent.w3cCredentialRepository.update(w3cCredentialRecord)

        return w3cCredentialRecord
    }

    override suspend fun getCredential(credentialId: String): AnonCredsCredentialInfo {
        val w3cCredentialRecord = agent.w3cCredentialRepository.findById(credentialId)
        logger.info("w3cCredentialRecord <> $w3cCredentialRecord")
        if (w3cCredentialRecord != null) return getAnoncredsCredentialInfoFromRecord(w3cCredentialRecord)

        val anonCredsCredentialRecord = agent.anonCredsCredentialRepository.getByCredentialId(credentialId)

        logger.warn("Querying legacy credential repository for credential with id $credentialId. Please run the migration script to migrate credentials to the new w3c format.")
        logger.info("Querying legacy credential repository for credential with id $credentialId. Please run the migration script to migrate credentials to the new w3c format.")
        return getAnoncredsCredentialInfoFromRecord(
            anonCredsCredentialRecord,
        )
    }

    override suspend fun createCredentialRequest(options: CreateCredentialRequestOptions): CreateCredentialRequestReturn {
        val useLegacyProverDid = options.useLegacyProverDid
        val credentialDefinition = options.credentialDefinition
        val credentialOffer = options.credentialOffer
        val linkSecretId = options.linkSecretId

        /*var linkSecretRecord : AnonCredsLinkSecretRecord? = agent.anonCredsLinkSecretRepository.findDefault()
        if (linkSecretId != null) {
            linkSecretRecord =
                agent.anonCredsLinkSecretRepository.getByLinkSecretId(linkSecretId)
        }

        if (linkSecretRecord == null) {
            if (agent.anoncredsmodulesconfig.autoCreateLinkSecret != null) {
                throw AnonCredsRsError("No link secret provided to createCredentialRequest and no default link secret has been found")
            }

            val (linkSecretId, linkSecretValue) = createLinkSecret()
            val options = StoreLinkSecretOptions(
                linkSecretId = linkSecretId,
                linkSecretValue = linkSecretValue,
                setAsDefault = true
            )
            linkSecretRecord = storeLinkSecret(options)
        }

        if (linkSecretRecord.value == null) {
            throw AnonCredsRsError("Link Secret value not stored")
        }*/

        val isLegacyIdentifier = Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialOffer.credDefId)

        if (!isLegacyIdentifier && useLegacyProverDid == true) {
            throw CredoError("Cannot use legacy prover_did with non-legacy identifiers")
        }

        val entropy = if ((useLegacyProverDid != null && !useLegacyProverDid) || !isLegacyIdentifier) Verifier().generateNonce() else null // [TODO] anoncreds came from uniffi
        val proverDid = if (useLegacyProverDid != null && useLegacyProverDid == true) {
            Base58.encode(Verifier().generateNonce().substring(0, 16).toByteArray())
        } else {
            null
        }

        val linkSecret = agent.anoncredsService.getLinkSecret(linkSecretId!!)
        val createReturnObj: CredentialRequestTuple = Prover().createCredentialRequest(
            entropy = entropy,
            proverDid = proverDid,
            credDef = CredentialDefinition(credentialDefinition),
            linkSecret = linkSecret,
            linkSecretId = linkSecretId,
            credOffer = CredentialOffer(credentialOffer.toJsonString()),
        )

        val credentialRequest = createReturnObj.request // CredentialRequest
        val credentialRequestMetadata = createReturnObj.metadata // CredentialRequestMetadata

        val anonCredsCredentialRequest = AnonCredsCredentialRequest.fromJsonString(credentialRequest.toJson())
        val anonCredsCredentialRequestMetadata = AnonCredsCredentialRequestMetadata.fromJsonString(credentialRequestMetadata.toJson())

        return CreateCredentialRequestReturn(
            credentialRequest = anonCredsCredentialRequest,
            credentialRequestMetadata = anonCredsCredentialRequestMetadata,
        )

//        } finally {
// //            createReturnObj.request.handle.clear()
// //            createReturnObj.metadata.handle.clear()
//        }
    }

    override suspend fun deleteCredential(credentialId: String) {
        val w3cCredentialRecord = agent.w3cCredentialRepository.findById(credentialId)

        if (w3cCredentialRecord != null) {
            agent.w3cCredentialRepository.delete(w3cCredentialRecord)
            return
        }

        val anoncredsCredentialRecord = agent.anonCredsCredentialRepository.getByCredentialId(credentialId)
        agent.anonCredsCredentialRepository.delete(anoncredsCredentialRecord)
    }

    override suspend fun createLinkSecret(options: CreateLinkSecretOptions?): CreateLinkSecretReturn {
        return CreateLinkSecretReturn(
            linkSecretId = options?.linkSecretId ?: BaseRecord.generateId(),
            linkSecret = anoncreds_uniffi.createLinkSecret(),
        )
    }

    private fun getAnoncredsCredentialInfoFromRecord(
        credentialRecord: Any,
        useUnqualifiedIdentifiersIfPresent: Boolean? = null,
    ): AnonCredsCredentialInfo { // can be W3cCredentialRecord | AnonCredsCredentialRecord
        if (credentialRecord is W3cCredentialRecord) {
            logger.info("credentialRecord is W3cCredentialRecord")
            return W3cAnonCredsUtils.anonCredsCredentialInfoFromW3cRecord(credentialRecord, useUnqualifiedIdentifiersIfPresent)
        } else {
            logger.info("credentialRecord is not W3cCredentialRecord")
            return W3cAnonCredsUtils.anonCredsCredentialInfoFromAnonCredsRecord(credentialRecord as AnonCredsCredentialRecord)
        }
    }

    private suspend fun storeLinkSecret(options: StoreLinkSecretOptions): AnonCredsLinkSecretRecord {
        val (linkSecretId, linkSecretValue, setAsDefault) = options
        val linkSecretRecord = AnonCredsLinkSecretRecord(linkSecretId = linkSecretId, value = linkSecretValue)

        val defaultLinkSecretRecord = agent.anonCredsLinkSecretRepository.findDefault()
        if (defaultLinkSecretRecord == null || setAsDefault) {
            linkSecretRecord.setTag("isDefault", true.toString())
        }

        if (defaultLinkSecretRecord != null && setAsDefault) {
            defaultLinkSecretRecord.setTag("isDefault", false.toString())
            agent.anonCredsLinkSecretRepository.update(defaultLinkSecretRecord)
        }

        agent.anonCredsLinkSecretRepository.save(linkSecretRecord)

        return linkSecretRecord
    }
}

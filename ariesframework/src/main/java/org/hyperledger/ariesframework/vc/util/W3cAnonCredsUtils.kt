package org.hyperledger.ariesframework.vc.util

import W3cCredentialSubject
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.Tags
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsClaimRecord
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsCredentialRecord
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers
import org.hyperledger.ariesframework.credentials.formats.anoncreds.MetadataKeys
import org.hyperledger.ariesframework.credentials.utils.Functions
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.vc.model.AnonCredsCredentialTags
import org.hyperledger.ariesframework.vc.model.W3cAnonCredsCredentialMetadata
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.slf4j.LoggerFactory

class W3cAnonCredsUtils {
    companion object {
        private val logger = LoggerFactory.getLogger(W3cAnonCredsUtils::class.java)
        fun getW3cRecordAnonCredsTags(
            credentialSubject: W3cCredentialSubject,
            issuerId: String,
            schemaId: String,
            schema: AnonCredsSchema,
            credentialDefinitionId: String,
            revocationRegistryId: String? = null,
            credentialRevocationId: String? = null,
            linkSecretId: String,
            methodName: String,
        ): Tags { // MutableMap<String, Any?> {

            val tags = mutableMapOf<String, Any?>(
                "anonCredsLinkSecretId" to linkSecretId,
                "anonCredsCredentialDefinitionId" to credentialDefinitionId,
                "anonCredsSchemaId" to schemaId,
                "anonCredsSchemaName" to schema.name,
                "anonCredsSchemaIssuerId" to schema.issuerId,
                "anonCredsSchemaVersion" to schema.version,
                "anonCredsMethodName" to methodName,
                "anonCredsRevocationRegistryId" to revocationRegistryId,
                "anonCredsCredentialRevocationId" to credentialRevocationId,
            )

            if (Indyidentifiers.isIndyDid(issuerId) || Indyidentifiers.isUnqualifiedIndyDid(issuerId)) {
                tags.putAll(
                    mapOf(
                        "anonCredsUnqualifiedIssuerId" to Indyidentifiers.getUnqualifiedDidIndyDid(issuerId),
                        "anonCredsUnqualifiedCredentialDefinitionId" to Indyidentifiers.getUnqualifiedDidIndyDid(credentialDefinitionId),
                        "anonCredsUnqualifiedSchemaId" to Indyidentifiers.getUnqualifiedDidIndyDid(schemaId),
                        "anonCredsUnqualifiedSchemaIssuerId" to Indyidentifiers.getUnqualifiedDidIndyDid(schema.issuerId),
                        "anonCredsUnqualifiedRevocationRegistryId" to revocationRegistryId?.let { Indyidentifiers.getUnqualifiedDidIndyDid(it) },
                    ),
                )
            }

            val claims = (credentialSubject.claims as? AnonCredsClaimRecord) ?: emptyMap()
            val values = Functions.mapAttributeRawValuesToAnonCredsCredentialValues(claims)

            values.forEach { (key, value) ->
                tags["anonCredsAttr::$key::value"] = value.raw
                tags["anonCredsAttr::$key::marker"] = true
            }

            return convertToTags(tags)
        }

        private fun convertToTags(map: MutableMap<String, Any?>): Tags {
            return map.mapNotNull { (key, value) ->
                (value as? String)?.let { key to it }
            }.toMap()
        }

        fun anonCredsCredentialInfoFromW3cRecord(w3cCredentialRecord: W3cCredentialRecord, useUnqualifiedIdentifiers: Boolean?): AnonCredsCredentialInfo {
            val w3c = w3cCredentialRecord.credential

            val w3cCredential: W3cJsonLdVerifiableCredential = W3cJsonLdVerifiableCredential(
                context = w3c.context,
                id = w3c.id,
                type = w3c.type,
                issuer = w3c.issuer,
                issuanceDate = w3c.issuanceDate,
                credentialSubject = w3c.credentialSubject,
                expirationDate = w3c.expirationDate,
                credentialSchema = w3c.credentialSchema,
                credentialStatus = w3c.credentialStatus,
            )
            if (w3cCredential.credentialSubject.size > 1) {
                throw CredoError("Credential subject must be an object, not an array.")
            }

            val anonCredsTags = getAnonCredsTagsFromRecord(w3cCredentialRecord)
            logger.info("tags: $anonCredsTags")
            if (anonCredsTags == null) {
                throw CredoError("AnonCreds tags not found on credential record.")
            }

            val w3cAnonCredsCredentialMetadataElement = w3cCredentialRecord.metadata.get(MetadataKeys.W3cAnonCredsCredentialMetadataKey)
                ?: throw CredoError("AnonCreds metadata not found on credential record.")

            val w3cAnonCredsCredentialMetadata = Json.decodeFromJsonElement<W3cAnonCredsCredentialMetadata>(
                serializer<W3cAnonCredsCredentialMetadata>(),
                w3cAnonCredsCredentialMetadataElement,
            )
            logger.info("w3cAnonCredsCredentialMetadata: $w3cAnonCredsCredentialMetadata")

            val credentialDefinitionId = anonCredsTags.unqualifiedCredentialDefinitionId
                ?.takeIf { useUnqualifiedIdentifiers == true }
                ?: anonCredsTags.credentialDefinitionId

            val schemaId = anonCredsTags.unqualifiedSchemaId
                ?.takeIf { useUnqualifiedIdentifiers == true }
                ?: anonCredsTags.schemaId

            val revocationRegistryId = anonCredsTags.unqualifiedRevocationRegistryId
                ?.takeIf { useUnqualifiedIdentifiers == true }
                ?: anonCredsTags.revocationRegistryId

            return AnonCredsCredentialInfo(
                credentialId = w3cCredentialRecord.id,
                attributes = (w3cCredential.credentialSubject.first().claims as AnonCredsClaimRecord),
                schemaId = schemaId,
                credentialDefinitionId = credentialDefinitionId,
                revocationRegistryId = revocationRegistryId,
                credentialRevocationId = w3cAnonCredsCredentialMetadata.credentialRevocationId,
                methodName = w3cAnonCredsCredentialMetadata.methodName,
                linkSecretId = w3cAnonCredsCredentialMetadata.linkSecretId,
                createdAt = w3cCredentialRecord.createdAt,
                updatedAt = w3cCredentialRecord.updatedAt ?: w3cCredentialRecord.createdAt,
            )
        }

        fun getAnonCredsTagsFromRecord(record: W3cCredentialRecord): AnonCredsCredentialTags? {
            logger.info("record: $record")

            val metadata = record.metadata.get(MetadataKeys.W3cAnonCredsCredentialMetadataKey) // as? W3cAnonCredsCredentialMetadata
            logger.info("metadata: $metadata")
            if (metadata == null) return null

            val tags = record.getTags() as? Map<String, String?> ?: return null

            logger.info("taaaags: $tags")
            val requiredKeys = listOf(
                "anonCredsLinkSecretId",
                "anonCredsMethodName",
                "anonCredsSchemaId",
                "anonCredsSchemaName",
                "anonCredsSchemaVersion",
                "anonCredsSchemaIssuerId",
                "anonCredsCredentialDefinitionId",
            )

            if (requiredKeys.any { tags[it].isNullOrBlank() }) return null

            return AnonCredsCredentialTags(
                linkSecretId = tags["anonCredsLinkSecretId"]!!,
                methodName = tags["anonCredsMethodName"]!!,
                schemaId = tags["anonCredsSchemaId"]!!,
                schemaName = tags["anonCredsSchemaName"]!!,
                schemaVersion = tags["anonCredsSchemaVersion"]!!,
                schemaIssuerId = tags["anonCredsSchemaIssuerId"]!!,
                credentialDefinitionId = tags["anonCredsCredentialDefinitionId"]!!,
                credentialRevocationId = tags["anonCredsCredentialRevocationId"],
                revocationRegistryId = tags["anonCredsRevocationRegistryId"],
                unqualifiedIssuerId = tags["anonCredsUnqualifiedIssuerId"],
                unqualifiedSchemaId = tags["anonCredsUnqualifiedSchemaId"],
                unqualifiedSchemaIssuerId = tags["anonCredsUnqualifiedSchemaIssuerId"],
                unqualifiedCredentialDefinitionId = tags["anonCredsUnqualifiedCredentialDefinitionId"],
                unqualifiedRevocationRegistryId = tags["anonCredsUnqualifiedRevocationRegistryId"],
                dynamicAttributes = tags.filterKeys { it.startsWith("anonCredsAttr::") },
            )
        }

        fun anonCredsCredentialInfoFromAnonCredsRecord(anonCredsCredentialRecord: AnonCredsCredentialRecord): AnonCredsCredentialInfo {
            val attributes = mutableMapOf<String, String>()

            for ((attribute, valueWrapper) in anonCredsCredentialRecord.credential.values) {
                attributes[attribute] = valueWrapper.raw
            }

            return AnonCredsCredentialInfo(
                credentialId = anonCredsCredentialRecord.id,
                attributes = attributes,
                schemaId = anonCredsCredentialRecord.credential.schemaId,
                credentialDefinitionId = anonCredsCredentialRecord.credential.credDefId,
                revocationRegistryId = anonCredsCredentialRecord.credential.revRegId,
                credentialRevocationId = anonCredsCredentialRecord.credentialRevocationId,
                methodName = anonCredsCredentialRecord.methodName,
                linkSecretId = anonCredsCredentialRecord.linkSecretId,
                createdAt = anonCredsCredentialRecord.createdAt,
                updatedAt = anonCredsCredentialRecord.updatedAt ?: anonCredsCredentialRecord.createdAt,
            )
        }
    }
}

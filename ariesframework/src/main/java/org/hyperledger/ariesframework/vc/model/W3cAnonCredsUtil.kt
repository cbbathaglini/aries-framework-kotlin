package org.hyperledger.ariesframework.vc.model

import W3cCredentialSubject
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsClaimRecord
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers
import org.hyperledger.ariesframework.credentials.utils.Functions

class W3cAnonCredsUtil {
    companion object{
        fun getW3cRecordAnonCredsTags(
            credentialSubject: W3cCredentialSubject,
            issuerId: String,
            schemaId: String,
            schema: AnonCredsSchema,
            credentialDefinitionId: String,
            revocationRegistryId: String? = null,
            credentialRevocationId: String? = null,
            linkSecretId: String,
            methodName: String
        ): MutableMap<String, Any?> {
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
                        "anonCredsUnqualifiedRevocationRegistryId" to revocationRegistryId?.let { Indyidentifiers.getUnqualifiedDidIndyDid(it) }
                    )
                )
            }

            val claims = (credentialSubject.claims as? AnonCredsClaimRecord) ?: emptyMap()
            val values = Functions.mapAttributeRawValuesToAnonCredsCredentialValues(claims)

            values.forEach { (key, value) ->
                tags["anonCredsAttr::$key::value"] = value.raw
                tags["anonCredsAttr::$key::marker"] = true
            }

            return tags
        }
    }
}
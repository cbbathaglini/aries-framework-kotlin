package org.hyperledger.ariesframework.anoncreds.service

import anoncreds_uniffi.Credential
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequestMetadata
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.LegacyToW3cCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.ProcessOptions
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialW3cOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsCredentialInfo
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateCredentialRequestReturn
import org.hyperledger.ariesframework.anoncreds.utils.LinkSecret
import org.hyperledger.ariesframework.credentials.formats.anoncreds.MetadataKeys
import org.hyperledger.ariesframework.credentials.modelv2.ProcessCredentialParams
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.vc.model.LinkedDataProof
import org.hyperledger.ariesframework.vc.model.LinkedDataProofBase
import org.hyperledger.ariesframework.vc.model.ProcessCredentialOptions
import org.hyperledger.ariesframework.vc.model.W3cAnonCredsCredentialMetadata
import org.hyperledger.ariesframework.vc.model.W3cAnonCredsUtil
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential

class AnonCredsRsHolderService (val agent: Agent): AnonCredsHolderService{
    override suspend fun storeCredential(
        options: StoreCredentialOptions,
        metadata: Map<String, Any>?
    ): String {
        val  credential = options.credential
        val  credentialDefinition = options.credentialDefinition
        val  credentialDefinitionId = options.credentialDefinitionId
        val  credentialRequestMetadata = options.credentialRequestMetadata
        val  schema = options.schema
        val  revocationRegistry = options.revocationRegistry

        var w3cJsonLdCredential : W3cJsonLdVerifiableCredential
        if (credential is W3cJsonLdVerifiableCredential){
            w3cJsonLdCredential = credential
        }else {
            val legacyToW3cCredentialOptions = LegacyToW3cCredentialOptions(
                credential = credential as AnonCredsCredential,
                issuerId = credentialDefinition.issuerId,
                processOptions = ProcessOptions(
                    credentialDefinition = credentialDefinition,
                    credentialRequestMetadata = credentialRequestMetadata,
                    revocationRegistryDefinition = revocationRegistry?.definition
                )
            )
            w3cJsonLdCredential = legacyToW3cCredential(legacyToW3cCredentialOptions)
        }


        val w3cCredentialRecord = storeW3cCredential(agentContext, {
            credentialRequestMetadata,
            credential: w3cJsonLdCredential,
            credentialDefinitionId,
            schema,
            credentialDefinition,
            revocationRegistryDefinition: revocationRegistry?.definition,
            revocationRegistryId: revocationRegistry?.id,
        })

        return w3cCredentialRecord.id
    }

    private fun legacyToW3cCredential(options: LegacyToW3cCredentialOptions) : W3cJsonLdVerifiableCredential {
        val ( credential, issuerId, processOptions ) = options
        var w3cCredential: W3cJsonLdVerifiableCredential

        var anonCredsCredential: Credential
        var w3cCredentialObj: W3cCredential

        try {
            anonCredsCredential = Credential.fromJson(credential as unknown as JsonObject)
            w3cCredentialObj = anonCredsCredential.toW3c({ issuerId, w3cVersion: '1.1' })

            val w3cJsonLdVerifiableCredential = JsonTransformer.fromJSON(
                    w3cCredentialObj.toJson(), W3cJsonLdVerifiableCredential
            )

            w3cCredential = w3cJsonLdVerifiableCredential
            if (processOptions != null){
                w3cCredential = this.processW3cCredential(agentContext, w3cJsonLdVerifiableCredential, processOptions)
            }

        } finally {
            anonCredsCredential?.handle?.clear()
            w3cCredentialObj?.handle?.clear()
        }

        return w3cCredential
    }

    private suspend fun processW3cCredential(credential: W3cJsonLdVerifiableCredential, processOptions: ProcessOptions) : W3cJsonLdVerifiableCredential {
        val (credentialDefinition, credentialRequestMetadata, revocationRegistryDefinition) = processOptions

        val processCredentialOptions = ProcessCredentialOptions(
            credentialRequestMetadata = credentialRequestMetadata,
            linkSecret = LinkSecret.getLinkSecret(agent, credentialRequestMetadata.link_secret_name),
            revocationRegistryDefinition = revocationRegistryDefinition,
            credentialDefinition = credentialDefinition
        )

        val credentialJson = credential.toJsonString()
        val w3cAnonCredsCredential = W3cCredential.fromJson(credentialJson)
        val processedW3cAnonCredsCredential = w3cAnonCredsCredential.process(processCredentialOptions)


        val processedCredentialJson = processedW3cAnonCredsCredential.toJson()
        val processedW3cJsonLdVerifiableCredential = W3cJsonLdVerifiableCredential.fromJson(processedCredentialJson)
        return processedW3cJsonLdVerifiableCredential
    }

    private suspend fun storeW3cCredential(options: StoreCredentialW3cOptions){
        val credential: W3cJsonLdVerifiableCredential = options.credential
        val credentialDefinitionId: String = options.credentialDefinitionId
        val schema: AnonCredsSchema = options.schema
        val credentialDefinition: AnonCredsCredentialDefinition = options.credentialDefinition
        val revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition? = options.revocationRegistryDefinition
        val revocationRegistryId: String? = options.revocationRegistryId
        val credentialRequestMetadata: AnonCredsCredentialRequestMetadata = options.credentialRequestMetadata

        val methodName = agent.anonCredsRegistryService
            .getRegistryForIdentifier(credential.issuer).methodName

        LinkSecret.getLinkSecret(agent, credentialRequestMetadata.link_secret_name)

        val credentialJson = credential.toJsonString()
        val w3cAnonCredsCredential = W3cCredential.fromJson(credentialJson)

        if (credential.credentialSubject is List<*>) {
            throw CredoError("Credential subject must be an object, not an array.")
        }

        val tags = W3cAnonCredsUtil.getW3cRecordAnonCredsTags(
            credentialSubject = credential.credentialSubject,
            issuerId = credential.issuer,
            schemaId = credentialDefinition.schemaId,
            schema = schema,
            credentialDefinitionId = credentialDefinitionId,
            revocationRegistryId = revocationRegistryId,
            credentialRevocationId = w3cAnonCredsCredential.revocationRegistryIndex?.toString(),
            linkSecretId = credentialRequestMetadata.link_secret_name,
            methodName = methodName
        )

        val w3cCredentialRecord = agent.w3cCredentialService.storeCredential(credential)

        val anonCredsCredentialMetadata: W3cAnonCredsCredentialMetadata = W3cAnonCredsCredentialMetadata(
                credentialRevocationId= tags["anonCredsCredentialRevocationId"].toString(),
                linkSecretId= tags["anonCredsLinkSecretId"].toString(),
                methodName= tags["anonCredsMethodName"].toString()
        )

        w3cCredentialRecord.setTags(tags)
        w3cCredentialRecord.metadata.set(MetadataKeys.W3cAnonCredsCredentialMetadataKey, anonCredsCredentialMetadata)

        agent.w3cCredentialRepository.update(w3cCredentialRecord)

        return w3cCredentialRecord

    }

    override suspend fun getCredential(credentialId: String): AnonCredsCredentialInfo {
        TODO("Not yet implemented")
    }

    override suspend fun createCredentialRequest(options: CreateCredentialRequestOptions): CreateCredentialRequestReturn {
        TODO("Not yet implemented")
    }

    override suspend fun deleteCredential(credentialId: String) {
        TODO("Not yet implemented")
    }
}
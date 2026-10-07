package org.hyperledger.ariesframework.anoncreds.service

import anoncreds_uniffi.CredentialDefinition
import anoncreds_uniffi.CredentialDefinitionPrivate
import anoncreds_uniffi.CredentialKeyCorrectnessProof
import anoncreds_uniffi.Issuer
import anoncreds_uniffi.CredentialOffer
import anoncreds_uniffi.CredentialRequest
import anoncreds_uniffi.CredentialRevocationConfig
import anoncreds_uniffi.RevocationRegistryDefinition
import anoncreds_uniffi.RevocationRegistryDefinitionPrivate
import anoncreds_uniffi.RevocationStatusList
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.exception.AnonCredsError
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialOffer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.anoncreds.model.issuer.CreateCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.issuer.CreateCredentialReturn
import org.hyperledger.ariesframework.anoncreds.repository.AnonCredsRevocationRegistryState
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers
import org.hyperledger.ariesframework.util.ConvertMapAnySerializer

class AnonCredsRsIssuerService(val agent: Agent) : AnonCredsIssuerService {
    override suspend fun createCredentialOffer(credentialDefinitionId: String): AnonCredsCredentialOffer {
        val credentialDefinitionRecord = agent.anoncredsCredentialDefinitionRepository
            .getByCredentialDefinitionId(credentialDefinitionId)
            ?: throw AnonCredsError("Credential Definition $credentialDefinitionId not found")

        val keyCorrectnessProofRecord = agent.anonCredsKeyCorrectnessProofRepository
            .getByCredentialDefinitionId(credentialDefinitionRecord.credentialDefinitionId)
            ?: throw AnonCredsError("Credential Definition $credentialDefinitionId not found")

        var schemaId = credentialDefinitionRecord.credentialDefinition.schemaId

        if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialDefinitionId)) {
            val parsed = Indyidentifiers.parseIndySchemaId(schemaId)
            schemaId = Indyidentifiers.getUnqualifiedSchemaId(
                unqualifiedDid = parsed.first,
                name = parsed.second,
                version = parsed.third,
            )
        }

        val keyProof = CredentialKeyCorrectnessProof(
            ConvertMapAnySerializer.mapAnyToJsonElement(keyCorrectnessProofRecord.value).toString(),
        )
        val credentialOffer = Issuer().createCredentialOffer(schemaId, credentialDefinitionId, keyProof)
        return Json.decodeFromString(credentialOffer.toJson())

    }

    override suspend fun createCredential(options: CreateCredentialOptions): CreateCredentialReturn {
        val credentialOffer = options.credentialOffer
        val credentialRequest = options.credentialRequest
        val credentialValues = options.credentialValues
        val revocationRegistryDefinitionId = options.revocationRegistryDefinitionId
        val revocationStatusList = options.revocationStatusList
        val revocationRegistryIndex = options.revocationRegistryIndex

        val revocationParams = listOf(
            revocationRegistryDefinitionId,
            revocationStatusList,
            revocationRegistryIndex,
        )

        if (revocationParams.filterNotNull().size in 1..2) {
            throw IllegalArgumentException(
                "Revocation requires all of revocationRegistryDefinitionId, revocationRegistryIndex and revocationStatusList",
            )
        }

        val attributeRawValues = mutableMapOf<String, String>()
        val attributeEncodedValues = mutableMapOf<String, String>()

        credentialValues.forEach { (key, value) ->
            attributeRawValues[key] = value.raw
            attributeEncodedValues[key] = value.encoded
        }

        val credentialDefinitionRecord = agent.anoncredsCredentialDefinitionRepository
            .getByCredentialDefinitionId(credentialRequest.credDefId)

        val credentialDefinitionPrivateRecord = agent.anonCredsCredentialDefinitionPrivateRepository
            .getByCredentialDefinitionId(credentialDefinitionRecord.credentialDefinitionId)

        var credentialDefinition = credentialDefinitionRecord.credentialDefinition

        if (Indyidentifiers.isUnqualifiedCredentialDefinitionId(credentialRequest.credDefId)) {
            val (namespaceIdentifier, schemaName, schemaVersion) = Indyidentifiers.parseIndySchemaId(credentialDefinition.schemaId)
            val unqualifiedDid = Indyidentifiers.parseIndyDid(credentialDefinition.issuerId).second
            credentialDefinition = credentialDefinition.copy(
                schemaId = Indyidentifiers.getUnqualifiedSchemaId(namespaceIdentifier, schemaName, schemaVersion),
                issuerId = unqualifiedDid,
            )
        }

        var revocationConfiguration: CredentialRevocationConfig? = null
        if (revocationRegistryDefinitionId != null && revocationStatusList != null && revocationRegistryIndex != null) {
            val revocationRegistryDefinitionRecord = agent.anonCredsRevocationRegistryDefinitionRepository
                .getByRevocationRegistryDefinitionId(revocationRegistryDefinitionId)

            val revDefPrivateRecord = agent.anonCredsRevocationRegistryDefinitionPrivateRepository
                .getByRevocationRegistryDefinitionId(revocationRegistryDefinitionId)

            if (revocationRegistryIndex >= revocationRegistryDefinitionRecord.revocationRegistryDefinition.value.maxCredNum) {
                revDefPrivateRecord.state = AnonCredsRevocationRegistryState.Full
            }

            val revocationRegistryDefinitionJson = Json.encodeToString(serializer<AnonCredsRevocationRegistryDefinition>(), revocationRegistryDefinitionRecord.revocationRegistryDefinition)
            val revocationRegistryDefinitionAnonCreds = RevocationRegistryDefinition(revocationRegistryDefinitionJson)

            val revocationRegistryDefinitionPrivateJson = revDefPrivateRecord.value as Map<String, String>
            val revocationRegistryDefinitionPrivateAnonCreds = Json.encodeToString(MapSerializer(String.serializer(), String.serializer()), revocationRegistryDefinitionPrivateJson)
            val revocationRegistryDefinitionPrivate = RevocationRegistryDefinitionPrivate(revocationRegistryDefinitionPrivateAnonCreds)

            val revocationStatusListAnoncredsJson = Json.encodeToString(serializer<AnonCredsRevocationStatusList>(), revocationStatusList)
            val revocationStatusListAnoncredsUniffi = RevocationStatusList(revocationStatusListAnoncredsJson)

            revocationConfiguration = CredentialRevocationConfig(
                regDef = revocationRegistryDefinitionAnonCreds,
                regDefPrivate = revocationRegistryDefinitionPrivate,
                statusList = revocationStatusListAnoncredsUniffi,
                registryIndex = revocationRegistryIndex.toUInt(),
            )
        }

        val credentialDefinitionJson = credentialDefinition.toJson()
        val credentialDefinitionUniffi = CredentialDefinition(credentialDefinitionJson)

        val credentialOfferJson = credentialOffer.toJsonString() // Json.encodeToString(AnonCredsCredentialOffer.serializer(), credentialOffer)
        val credentialOfferUniffi = CredentialOffer(credentialOfferJson)

        val credentialRequestJson = Json.encodeToString(serializer<AnonCredsCredentialRequest>(), credentialRequest)
        val credentialRequestUniffi = CredentialRequest(credentialRequestJson)

        val credentialDefinitionPrivate = CredentialDefinitionPrivate(
            ConvertMapAnySerializer.mapAnyToJsonElement(credentialDefinitionPrivateRecord.value).toString(),
        )
        val credential = Issuer().createCredential(
            credentialDefinitionUniffi,
            credentialDefinitionPrivate,
            credentialOfferUniffi,
            credentialRequestUniffi,
            attributeRawValues,
            attributeEncodedValues,
            revocationConfiguration,
        )

        return CreateCredentialReturn(
            credential = Json.decodeFromString<AnonCredsCredential>(credential.toJson()),
            credentialRevocationId = credential.revRegIndex()?.toString(),
        )
    }
}

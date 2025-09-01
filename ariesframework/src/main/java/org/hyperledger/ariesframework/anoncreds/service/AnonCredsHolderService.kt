package org.hyperledger.ariesframework.anoncreds.service

import anoncreds_uniffi.CredentialDefinition
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.GetCredentialsForProofRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialInfo
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProof
import org.hyperledger.ariesframework.anoncreds.model.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.LegacyToW3cCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateCredentialRequestReturn
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateLinkSecretOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateLinkSecretReturn
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateProofOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.GetCredentialsForProofRequestReturn
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential

interface AnonCredsHolderService {

    suspend fun storeCredential(
        options: StoreCredentialOptions,
        metadata: Map<String, Any>? = null
    ): String

    suspend fun getCredential(
        credentialId: String,
        useUnqualifiedIdentifiersIfPresent: Boolean? = null
    ): AnonCredsCredentialInfo

    suspend fun createCredentialRequest(
        options: CreateCredentialRequestOptions
    ): CreateCredentialRequestReturn

    suspend fun deleteCredential(
        credentialId: String
    )

    suspend fun createLinkSecret(
        options: CreateLinkSecretOptions? = null
    ): CreateLinkSecretReturn

    suspend fun legacyToW3cCredential(
        options: LegacyToW3cCredentialOptions
    ): W3cJsonLdVerifiableCredential

    suspend fun createProof(
        options: CreateProofOptions
    ): AnonCredsProof? //remover ?

    //
//    suspend fun getCredentials(
//        options: GetCredentialsOptions
//    ): List<AnonCredsCredentialInfo>

    suspend fun getCredentialsForProofRequest(
        options: GetCredentialsForProofRequestOptions
    ): GetCredentialsForProofRequestReturn


//    suspend fun createW3cPresentation(
//        options: CreateW3cPresentationOptions
//    ): W3cJsonLdVerifiablePresentation
//
//    suspend fun w3cToLegacyCredential(
//        options: W3cToLegacyCredentialOptions
//    ): AnonCredsCredential
//

}
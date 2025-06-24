package org.hyperledger.ariesframework.anoncreds.service

import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsCredentialInfo
import org.hyperledger.ariesframework.anoncreds.model.CreateCredentialRequestOptions
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.holder.CreateCredentialRequestReturn

interface AnonCredsHolderService {

    suspend fun storeCredential(
        options: StoreCredentialOptions,
        metadata: Map<String, Any>? = null
    ): String

    suspend fun getCredential(
        credentialId: String
    ): AnonCredsCredentialInfo

    suspend fun createCredentialRequest(
        options: CreateCredentialRequestOptions
    ): CreateCredentialRequestReturn

    suspend fun deleteCredential(
        credentialId: String
    )

//    suspend fun createLinkSecret(
//        options: CreateLinkSecretOptions
//    ): CreateLinkSecretReturn
//
//    suspend fun createProof(
//        options: CreateProofOptions
//    ): AnonCredsProof

    //
//    suspend fun getCredentials(
//        options: GetCredentialsOptions
//    ): List<AnonCredsCredentialInfo>

//    suspend fun getCredentialsForProofRequest(
//        options: GetCredentialsForProofRequestOptions
//    ): GetCredentialsForProofRequestReturn

//    suspend fun createW3cPresentation(
//        options: CreateW3cPresentationOptions
//    ): W3cJsonLdVerifiablePresentation
//
//    suspend fun w3cToLegacyCredential(
//        options: W3cToLegacyCredentialOptions
//    ): AnonCredsCredential
//
//    suspend fun legacyToW3cCredential(
//        options: LegacyToW3cCredentialOptions
//    ): W3cJsonLdVerifiableCredential
}
package org.hyperledger.ariesframework.vc.dataintegrity

import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.modules.W3cCredentialsModuleConfig

class W3cJsonLdCredentialService(
    private val signatureSuiteRegistry: SignatureSuiteRegistry,
    private val w3cCredentialsModuleConfig: W3cCredentialsModuleConfig
) {

    suspend fun getExpandedTypesForCredential(
        credential: W3cJsonLdVerifiableCredential
    ): List<String> {
        val credentialJson = credential.toJsonString()
        val documentLoader = w3cCredentialsModuleConfig.documentLoader

        val expanded = jsonLd.expand(
            credentialJson,
            mapOf("documentLoader" to documentLoader)
        )

        val firstExpanded = (expanded as? List<Map<String, Any?>>)?.firstOrNull()
        val types = firstExpanded?.get("@type")

        return when (types) {
            is String -> listOf(types)
            is List<*> -> types.filterIsInstance<String>()
            else -> emptyList()
        }
    }
}
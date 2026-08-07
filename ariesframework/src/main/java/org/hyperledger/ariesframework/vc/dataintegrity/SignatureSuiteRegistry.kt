package org.hyperledger.ariesframework.vc.dataintegrity

import org.hyperledger.ariesframework.util.SupportedPublicJwkClass

class SignatureSuiteRegistry(
    suites: List<SuiteInfo>,
) {
    private val suiteMapping: List<SuiteInfo> = suites.filterNot { it.isDefault }

    val supportedProofTypes: List<String>
        get() = suiteMapping.map { it.proofType }

    @Deprecated("Use getByProofType or getAllByPublicJwkType instead")
    fun getByVerificationMethodType(verificationMethodType: String): SuiteInfo? {
        return suiteMapping.find { it.verificationMethodTypes.contains(verificationMethodType) }
    }

    fun getAllByPublicJwkType(publicJwkType: SupportedPublicJwkClass): List<SuiteInfo> {
        return suiteMapping.filter { it.supportedPublicJwkTypes.contains(publicJwkType) }
    }

    fun getByProofType(proofType: String): SuiteInfo {
        return suiteMapping.find { it.proofType == proofType }
            ?: throw IllegalArgumentException("No signature suite for proof type: $proofType")
    }

    fun getVerificationMethodTypesByProofType(proofType: String): List<String> {
        return suiteMapping.find { it.proofType == proofType }?.verificationMethodTypes
            ?: throw IllegalArgumentException("No verification method type found for proof type: $proofType")
    }
}

data class SuiteInfo(
    val suiteClass: Any, // typeof LinkedDataSignature
    val proofType: String,
    val verificationMethodTypes: List<String>,
    val supportedPublicJwkTypes: List<SupportedPublicJwkClass>,
    val isDefault: Boolean = false,
)

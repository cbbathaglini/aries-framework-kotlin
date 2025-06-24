package org.hyperledger.ariesframework.vc.service

import org.hyperledger.ariesframework.vc.dataintegrity.SignatureSuiteRegistry
import org.hyperledger.ariesframework.vc.modules.W3cCredentialsModuleConfig

data class W3cJsonLdCredentialService (
    private val signatureSuiteRegistry: SignatureSuiteRegistry,
    private val w3cCredentialsModuleConfig: W3cCredentialsModuleConfig
){
}
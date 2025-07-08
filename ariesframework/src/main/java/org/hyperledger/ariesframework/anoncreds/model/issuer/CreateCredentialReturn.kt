package org.hyperledger.ariesframework.anoncreds.model.issuer

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredential

data class CreateCredentialReturn(
    val credential: AnonCredsCredential,
    val credentialRevocationId: String? = null
){
    override fun toString(): String {
        return "CreateCredentialReturn(credential=$credential, credentialRevocationId=$credentialRevocationId)"
    }
}
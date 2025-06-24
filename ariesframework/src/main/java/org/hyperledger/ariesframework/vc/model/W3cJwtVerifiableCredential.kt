package org.hyperledger.ariesframework.vc.model

data class W3cJwtVerifiableCredential(
    val jwt: String
) : W3cVerifiableCredential()
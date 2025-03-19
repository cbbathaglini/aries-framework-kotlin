package org.hyperledger.ariesframework.credentials.v2.models

import kotlinx.serialization.Serializable

@Serializable
enum class CredentialRole {
    Holder,
    Issuer,
}

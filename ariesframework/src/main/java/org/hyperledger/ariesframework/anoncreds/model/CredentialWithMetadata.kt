package org.hyperledger.ariesframework.anoncreds.model

import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

data class CredentialWithMetadata(
    val credentialInfo: AnonCredsCredentialInfo,
    val interval: AnonCredsNonRevokedInterval?,
)

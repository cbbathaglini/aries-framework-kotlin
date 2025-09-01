package org.hyperledger.ariesframework.anoncreds.service

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialOffer
import org.hyperledger.ariesframework.anoncreds.model.issuer.CreateCredentialOptions
import org.hyperledger.ariesframework.anoncreds.model.issuer.CreateCredentialReturn

interface AnonCredsIssuerService {

    suspend fun createCredentialOffer(
        credentialDefinitionId: String,
    ): AnonCredsCredentialOffer

    suspend fun createCredential(
        options: CreateCredentialOptions,
    ): CreateCredentialReturn
}

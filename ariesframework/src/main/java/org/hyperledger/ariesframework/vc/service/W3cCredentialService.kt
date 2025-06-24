package org.hyperledger.ariesframework.vc.service

import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.model.W3cVerifiableCredential
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRepository

class W3cCredentialService(
    private val w3cCredentialRepository: W3cCredentialRepository,
    private val w3cJsonLdCredentialService: W3cJsonLdCredentialService,
    private val w3cJwtCredentialService: W3cJwtCredentialService)
{

    /**
     * Writes a credential to storage
     *
     * @param record the credential to be stored
     * @returns the credential record that was written to storage
     */
    suspend fun storeCredential(verifiableCredential: W3cVerifiableCredential): W3cCredentialRecord {
        val expandedTypes: List<String> = when (verifiableCredential) {
            is W3cJsonLdVerifiableCredential -> {
                w3cJsonLdCredentialService.getExpandedTypesForCredential(verifiableCredential)
            }
            else -> emptyList()
        }

        val w3cCredentialRecord = W3cCredentialRecord(
            tags = expandedTypes,
            credential = verifiableCredential
        )

        w3cCredentialRepository.save(w3cCredentialRecord)

        return w3cCredentialRecord
    }

}
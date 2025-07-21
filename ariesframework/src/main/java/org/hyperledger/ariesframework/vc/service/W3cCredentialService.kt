package org.hyperledger.ariesframework.vc.service

import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.anoncreds.model.StoreCredentialOptions
import org.hyperledger.ariesframework.anoncreds.service.AnonCredsRsHolderService
import org.hyperledger.ariesframework.vc.dataintegrity.W3cJsonLdCredentialService
import org.hyperledger.ariesframework.vc.model.W3cCredential
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.model.W3cVerifiableCredential
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRepository
import org.slf4j.LoggerFactory
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic


class W3cCredentialService(
    private val w3cCredentialRepository: W3cCredentialRepository,
    private val w3cJsonLdCredentialService: W3cJsonLdCredentialService,
    private val w3cJwtCredentialService: W3cJwtCredentialService)
{

    private val logger = LoggerFactory.getLogger(W3cCredentialService::class.java)
    /**
     * Writes a credential to storage
     *
     * @param record the credential to be stored
     * @returns the credential record that was written to storage
     */
    suspend fun storeCredentialW3cJsonLdVerifiableCredential(jsonLdVerifiableCredential: W3cJsonLdVerifiableCredential): W3cCredentialRecord {
        //val expandedTypes: Map<String, String> =  w3cJsonLdCredentialService.getExpandedTypesForCredential(jsonLdVerifiableCredential)
        val expandedTypes: Map<String, String> = mapOf("type" to "https://www.w3.org/2018/credentials#VerifiableCredential")
        logger.info("expandedTypes: ${expandedTypes.toString()}")


        val w3cCredential = W3cCredential(
            context = jsonLdVerifiableCredential.context,
            id = jsonLdVerifiableCredential.id,
            type = jsonLdVerifiableCredential.type,
            issuer = jsonLdVerifiableCredential.issuer,
            issuanceDate = jsonLdVerifiableCredential.issuanceDate,
            credentialSubject = jsonLdVerifiableCredential.credentialSubject,
            expirationDate = jsonLdVerifiableCredential.expirationDate,
            credentialSchema = jsonLdVerifiableCredential.credentialSchema,
            credentialStatus = jsonLdVerifiableCredential.credentialStatus
        )

        val w3cCredentialRecord = W3cCredentialRecord(
            tags = expandedTypes,
            credential = w3cCredential
        )

        logger.info("w3cCredentialRecord =====> ${w3cCredentialRecord.toString()}")
        w3cCredentialRepository.save(w3cCredentialRecord)
        return w3cCredentialRecord
    }

}
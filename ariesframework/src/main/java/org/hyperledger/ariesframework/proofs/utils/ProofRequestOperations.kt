package org.hyperledger.ariesframework.proofs.utils

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequestRestriction
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers.isUnqualifiedCredentialDefinitionId
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers.isUnqualifiedIndyDid
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers.isUnqualifiedRevocationRegistryId
import org.hyperledger.ariesframework.anoncreds.utils.Indyidentifiers.isUnqualifiedSchemaId

class ProofRequestOperations {
    companion object {
        fun proofRequestUsesUnqualifiedIdentifiers(proofRequest: AnonCredsProofRequest): Boolean {
            fun List<AnonCredsProofRequestRestriction>?.hasUnqualified(): Boolean =
                this?.any { r ->
                    (r.credDefId?.let(::isUnqualifiedCredentialDefinitionId) == true) ||
                        (r.schemaId?.let(::isUnqualifiedSchemaId) == true) ||
                        (r.issuerDid?.let(::isUnqualifiedIndyDid) == true) ||
                        (r.issuerId?.let(::isUnqualifiedIndyDid) == true) ||
                        (r.schemaIssuerDid?.let(::isUnqualifiedIndyDid) == true) ||
                        (r.schemaIssuerId?.let(::isUnqualifiedIndyDid) == true) ||
                        (r.revRegId?.let(::isUnqualifiedRevocationRegistryId) == true)
                } ?: false

            return proofRequest.requestedAttributes.values.any { it.restrictions.hasUnqualified() } ||
                proofRequest.requestedPredicates.values.any { it.restrictions.hasUnqualified() }
        }
    }
}

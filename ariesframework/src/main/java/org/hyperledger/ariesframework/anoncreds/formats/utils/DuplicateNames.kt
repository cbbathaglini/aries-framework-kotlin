package org.hyperledger.ariesframework.anoncreds.formats.utils

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.error.CredoError

class DuplicateNames {

    companion object{
         fun assertNoDuplicateGroupsNamesInProofRequest(proofRequest: AnonCredsProofRequest) {
            val attributes = attributeNamesToArray(proofRequest)
            val predicates = predicateNamesToArray(proofRequest)

            val duplicates = predicates.filter { attributes.contains(it) }
            if (duplicates.isNotEmpty()) {
                throw CredoError(
                    "The proof request contains duplicate predicates and attributes: ${duplicates.joinToString(",")}"
                )
            }
        }

        fun attributeNamesToArray(proofRequest: AnonCredsProofRequest): List<String> {
            return proofRequest.requestedAttributes
                .values
                .flatMap { attr ->
                    when {
                        attr.name != null -> listOf(attr.name)
                        attr.names != null -> attr.names
                        else -> emptyList()
                    }
                }
        }

        fun predicateNamesToArray(proofRequest: AnonCredsProofRequest): List<String> {
            return proofRequest.requestedPredicates
                .values
                .map { it.name }
                .toSet()
                .toList()
        }
    }
}
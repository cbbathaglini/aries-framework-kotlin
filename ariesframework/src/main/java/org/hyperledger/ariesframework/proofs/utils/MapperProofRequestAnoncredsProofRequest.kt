package org.hyperledger.ariesframework.proofs.utils

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequestRestriction
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedAttribute
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRequestedPredicate
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.proofs.models.AttributeFilter
import org.hyperledger.ariesframework.proofs.models.ProofAttributeInfo
import org.hyperledger.ariesframework.proofs.models.ProofPredicateInfo
import org.hyperledger.ariesframework.proofs.models.RevocationInterval

class MapperProofRequestAnoncredsProofRequest {

    companion object {
        fun toAnonCredsRequestedAttribute(proofAtributeInfo: Map<String, ProofAttributeInfo>): Map<String, AnonCredsRequestedAttribute> {
            return proofAtributeInfo.mapValues { (_, attr) ->
                AnonCredsRequestedAttribute(
                    name = attr.name,
                    names = attr.names,
                    restrictions = attr.restrictions?.map { toAnonCredsRestriction(it) },
                    nonRevoked = attr.nonRevoked?.let { toAnonCredsTimeInterval(it) }
                )
            }
        }

        fun toAnonCredsTimeInterval(revocationInterval: RevocationInterval?): AnonCredsNonRevokedInterval {
            return AnonCredsNonRevokedInterval(
                from = revocationInterval?.from?.toLong(),
                to = revocationInterval?.to?.toLong()
            )
        }

        fun toAnonCredsRequestedPredicate(proofPredicateInfo: Map<String, ProofPredicateInfo>): Map<String, AnonCredsRequestedPredicate> {
            return proofPredicateInfo.mapValues { (_, attr) ->
                AnonCredsRequestedPredicate(
                    name = attr.name,
                    pType = attr.predicateType,
                    pValue = attr.predicateValue.toLong(),
                    restrictions = attr.restrictions?.map { toAnonCredsRestriction(it) },
                    nonRevoked = toAnonCredsTimeInterval(attr.nonRevoked)
                )
            }
        }

        fun toAnonCredsRestriction(attributeFilter: AttributeFilter): AnonCredsProofRequestRestriction {
            return AnonCredsProofRequestRestriction(
                schemaId = attributeFilter.schemaId,
                credDefId = attributeFilter.credentialDefinitionId,
                issuerDid = attributeFilter.issuerDid,
                schemaVersion = attributeFilter.schemaVersion,
                schemaName = attributeFilter.schemaName,
                schemaIssuerId = attributeFilter.schemaIssuerDid
            )
        }
    }
}
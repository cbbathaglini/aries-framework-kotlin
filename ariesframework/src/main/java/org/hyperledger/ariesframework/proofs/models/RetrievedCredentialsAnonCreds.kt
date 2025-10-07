package org.hyperledger.ariesframework.proofs.models

import kotlinx.serialization.Serializable

@Serializable
class RetrievedCredentialsAnonCreds {
    var requestedAttributes: MutableMap<String, List<RequestedAttributeAnonCreds>> = mutableMapOf()
    var requestedPredicates: MutableMap<String, List<RequestedPredicateAnonCreds>> = mutableMapOf()
}

package org.hyperledger.ariesframework.proofs.models

class RetrievedCredentialsAnonCreds {
    var requestedAttributes: MutableMap<String, List<RequestedAttributeAnonCreds>> = mutableMapOf()
    var requestedPredicates: MutableMap<String, List<RequestedPredicateAnonCreds>> = mutableMapOf()
}

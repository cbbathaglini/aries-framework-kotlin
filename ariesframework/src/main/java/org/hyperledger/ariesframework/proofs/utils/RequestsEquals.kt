package org.hyperledger.ariesframework.proofs.utils

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsProofRequest
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval

class RequestsEquals {
    companion object {
        // ---- Deep equality para estruturas "JSON-like" ----
        fun areObjectsEqual(a: Any?, b: Any?): Boolean {
            if (a === b) return true
            if (a == null || b == null) return false

            return when {
                a is Map<*, *> && b is Map<*, *> -> {
                    if (a.size != b.size) return false
                    // Mesma chave -> valores iguais recursivamente
                    a.keys.all { k ->
                        b.containsKey(k) && areObjectsEqual(a[k], b[k])
                    } && b.keys.all { k ->
                        a.containsKey(k) && areObjectsEqual(b[k], a[k])
                    }
                }
                a is List<*> && b is List<*> -> {
                    if (a.size != b.size) return false
                    // Here order matters (same as default behavior); in TS
                    // the unordered comparison is done by the caller (e.g., areRestrictionsEqual)
                    a.indices.all { i -> areObjectsEqual(a[i], b[i]) }
                }
                else -> a == b
            }
        }

        /**
         * Checks if two name arrays are equivalent (order does not matter).
         * Rules:
         * - null == null/empty
         * - no duplicates allowed on either side
         * - same set of names
         */
        fun areNamesEqual(namesA: List<String>?, namesB: List<String>?): Boolean {
            if (namesA == null) return namesB == null || namesB.isEmpty()
            if (namesB == null) return namesA.isEmpty()

            // Duplicates not allowed (as in TS)
            if (namesA.toSet().size != namesA.size) return false
            if (namesB.toSet().size != namesB.size) return false

            if (namesA.size != namesB.size) return false

            return namesA.all { it in namesB }
        }

        fun isNonRevokedEqual(
            nonRevokedA: AnonCredsNonRevokedInterval?,
            nonRevokedB: AnonCredsNonRevokedInterval?,
        ): Boolean {
            if (nonRevokedA == null) {
                return nonRevokedB == null || (nonRevokedB.from == null && nonRevokedB.to == null)
            }
            if (nonRevokedB == null) {
                return nonRevokedA.from == null && nonRevokedA.to == null
            }

            return nonRevokedA.from == nonRevokedB.from && nonRevokedA.to == nonRevokedB.to
        }

        /**
         * Equality of restriction lists (order does not matter).
         * Implements the same logic as TS: for each item in A, find an equal one in B (deep comparison),
         * removing it from the B list when matched, to respect multiplicity.
         */
        fun <T> areRestrictionsEqual(
            restrictionsA: List<T>?,
            restrictionsB: List<T>?,
        ): Boolean {
            if (restrictionsA == null) return restrictionsB == null || restrictionsB.isEmpty()
            if (restrictionsB == null) return restrictionsA.isEmpty()

            val bList = restrictionsB.toMutableList()

            return restrictionsA.all { aItem ->
                val idx = bList.indexOfFirst { bItem -> areObjectsEqual(aItem, bItem) }
                if (idx != -1) {
                    bList.removeAt(idx)
                    true
                } else {
                    false
                }
            }
        }

        fun areAnonCredsProofRequestsEqual(
            requestA: AnonCredsProofRequest,
            requestB: AnonCredsProofRequest,
        ): Boolean {
            // Top-level non-revoked
            if (!isNonRevokedEqual(requestA.nonRevoked, requestB.nonRevoked)) return false

            // ----- Atributos -----
            val attributeAList = requestA.requestedAttributes.values.toMutableList()
            val attributeBList = requestB.requestedAttributes.values.toMutableList()

            if (attributeAList.size != attributeBList.size) return false

            val attributesMatch = attributeAList.all { a ->
                val bIndex = attributeBList.indexOfFirst { b ->
                    (b.name == a.name) &&
                        areNamesEqual(a.names, b.names) &&
                        isNonRevokedEqual(a.nonRevoked, b.nonRevoked) &&
                        areRestrictionsEqual(a.restrictions, b.restrictions)
                }
                if (bIndex != -1) {
                    attributeBList.removeAt(bIndex)
                    true
                } else {
                    false
                }
            }
            if (!attributesMatch) return false

            // ----- Predicados -----
            val predicatesA = requestA.requestedPredicates.values.toMutableList()
            val predicatesB = requestB.requestedPredicates.values.toMutableList()

            if (predicatesA.size != predicatesB.size) return false

            val predicatesMatch = predicatesA.all { a ->
                val bIndex = predicatesB.indexOfFirst { b ->
                    a.name == b.name &&
                        a.pType == b.pType &&
                        a.pValue == b.pValue &&
                        isNonRevokedEqual(a.nonRevoked, b.nonRevoked) &&
                        areRestrictionsEqual(a.restrictions, b.restrictions)
                }
                if (bIndex != -1) {
                    predicatesB.removeAt(bIndex)
                    true
                } else {
                    false
                }
            }

            return predicatesMatch
        }
    }
}

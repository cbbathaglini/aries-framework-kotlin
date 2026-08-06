package org.hyperledger.ariesframework.anoncreds.formats.utils

import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedAttributeMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedPredicateMatch
import java.util.Date

class SortRequestedCredentialsMatches {
    companion object {
        // Generic: you provide how to extract revoked and updatedAt
        fun <T> sortRequestedCredentialsMatches(
            credentials: List<T>,
            revokedOf: (T) -> Boolean?,
            updatedAtOf: (T) -> Date,
        ): List<T> {
            // rank: null -> 0, false -> 1, true -> 2
            fun rank(revoked: Boolean?): Int = when (revoked) {
                null -> 0
                false -> 1
                true -> 2
            }

            // sortedWith creates a NEW list (does not modify the original)
            return credentials.sortedWith { a, b ->
                val ra = rank(revokedOf(a))
                val rb = rank(revokedOf(b))
                if (ra != rb) {
                    ra - rb
                } else {
                    // tie-break by updatedAt DESC (most recent first)
                    val ta = updatedAtOf(a).time
                    val tb = updatedAtOf(b).time
                    (tb - ta).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                }
            }
        }

        // Atalho: atributos
        fun sortRequestedCredentialsAttrMatches(
            credentials: List<AnonCredsRequestedAttributeMatch>,
        ): List<AnonCredsRequestedAttributeMatch> =
            sortRequestedCredentialsMatches(
                credentials,
                revokedOf = { it.revoked },
                updatedAtOf = { Date(it.credentialInfo.updatedAt.toEpochMilliseconds()) },
            )

        // Atalho: predicados
        fun sortRequestedCredentialsPredicatesMatches(
            credentials: List<AnonCredsRequestedPredicateMatch>,
        ): List<AnonCredsRequestedPredicateMatch> =
            sortRequestedCredentialsMatches(
                credentials,
                revokedOf = { it.revoked },
                updatedAtOf = { Date(it.credentialInfo.updatedAt.toEpochMilliseconds()) },
            )
    }
}

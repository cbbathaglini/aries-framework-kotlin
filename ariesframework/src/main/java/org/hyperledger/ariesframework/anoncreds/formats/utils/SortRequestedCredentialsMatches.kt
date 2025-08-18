package org.hyperledger.ariesframework.anoncreds.formats.utils

import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedAttributeMatch
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsRequestedPredicateMatch
import java.util.Date

class SortRequestedCredentialsMatches {
    companion object{
        // Genérica: você informa como extrair revoked e updatedAt
        fun <T> sortRequestedCredentialsMatches(
            credentials: List<T>,
            revokedOf: (T) -> Boolean?,
            updatedAtOf: (T) -> Date
        ): List<T> {
            // rank: null -> 0, false -> 1, true -> 2
            fun rank(revoked: Boolean?): Int = when (revoked) {
                null -> 0
                false -> 1
                true -> 2
            }

            // sortedWith cria uma NOVA lista (não modifica a original)
            return credentials.sortedWith { a, b ->
                val ra = rank(revokedOf(a))
                val rb = rank(revokedOf(b))
                if (ra != rb) {
                    ra - rb
                } else {
                    // desempate por updatedAt DESC (mais recente primeiro)
                    val ta = updatedAtOf(a).time
                    val tb = updatedAtOf(b).time
                    (tb - ta).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
                }
            }
        }

        // Atalho: atributos
        fun sortRequestedCredentialsMatches(
            credentials: List<AnonCredsRequestedAttributeMatch>
        ): List<AnonCredsRequestedAttributeMatch> =
            sortRequestedCredentialsMatches(
                credentials,
                revokedOf = { it.revoked },
                updatedAtOf = { Date(it.credentialInfo.updatedAt.toEpochMilliseconds())}
            )

        // Atalho: predicados
        fun sortRequestedCredentialsMatches(
            credentials: List<AnonCredsRequestedPredicateMatch>
        ): List<AnonCredsRequestedPredicateMatch> =
            sortRequestedCredentialsMatches(
                credentials,
                revokedOf = { it.revoked },
                updatedAtOf = { Date(it.credentialInfo.updatedAt.toEpochMilliseconds())}
            )
    }
}
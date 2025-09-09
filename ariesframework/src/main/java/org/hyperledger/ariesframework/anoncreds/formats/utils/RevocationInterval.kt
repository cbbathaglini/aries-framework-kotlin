package org.hyperledger.ariesframework.anoncreds.formats.utils

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.anoncreds.model.holder.AnonCredsNonRevokedInterval
import org.hyperledger.ariesframework.error.CredoError

class RevocationInterval {
    companion object {
        fun assertBestPracticeRevocationInterval(
            revocationInterval: AnonCredsNonRevokedInterval
        ): BestPracticeNonRevokedInterval {
            if (revocationInterval.to == null) {
                throw CredoError(
                    "Presentation requests proof of non-revocation with no 'to' value specified"
                )
            }

            if ((revocationInterval.from != null || revocationInterval.from?.toInt() == 0) &&
                revocationInterval.to != revocationInterval.from
            ) {
                throw CredoError(
                    "Presentation requests proof of non-revocation with an interval from: '${revocationInterval.from}' " +
                            "that does not match the interval to: '${revocationInterval.to}', as specified in Aries RFC 0441"
                )
            }

            val bestPracticeNonRevokedInterval = BestPracticeNonRevokedInterval(revocationInterval.from!!.toInt(), revocationInterval.to.toInt())
            return bestPracticeNonRevokedInterval
        }
    }
}

@Serializable
data class BestPracticeNonRevokedInterval(
    val from: Int? = null,
    val to: Int
)
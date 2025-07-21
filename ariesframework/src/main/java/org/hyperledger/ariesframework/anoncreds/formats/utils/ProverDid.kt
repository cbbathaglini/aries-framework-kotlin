package org.hyperledger.ariesframework.anoncreds.formats.utils

import org.hyperledger.ariesframework.util.Base58
import java.util.UUID

class ProverDid {
    companion object{
        fun generateLegacyProverDidLikeString(): String {
            val uuidBytes = UUID.randomUUID().toString().toByteArray()
            val sliced = uuidBytes.copyOfRange(0, 16)
            return Base58.encode(sliced)
        }
    }
}
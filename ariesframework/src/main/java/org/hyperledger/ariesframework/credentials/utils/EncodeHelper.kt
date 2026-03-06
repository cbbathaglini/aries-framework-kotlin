package org.hyperledger.ariesframework.credentials.utils

import android.util.Log
import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsEncoder

class EncodeHelper {
    companion object {
        data class EncodeTemp(
            val referent: String,
            val raw: Any?,
            val encoded: String,
            val expected: String,
        )

        fun logEncodingSample(tag: String, whenLabel: String, sample: EncodeTemp) {
            Log.d("AriesIntegration", "==== $whenLabel ($tag) ====")
            Log.d("AriesIntegration", "atributo : ${sample.referent}")
            Log.d("AriesIntegration", "valor do atributo : ${sample.raw}")
            Log.d("AriesIntegration", "encoded recebido : ${sample.encoded}")
            Log.d("AriesIntegration", "encoded esperado : ${sample.expected}")
            Log.d("AriesIntegration", "match? : ${sample.expected == sample.encoded}")
        }

        fun buildEncSample(referent: String, raw: Any?, encoded: String): EncodeTemp {
            val expected = AnonCredsEncoder.encodeCredentialValue(raw)
            return EncodeTemp(
                referent = referent,
                raw = raw,
                encoded = encoded,
                expected = expected,
            )
        }
    }
}

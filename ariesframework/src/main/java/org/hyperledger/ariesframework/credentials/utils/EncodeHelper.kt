package org.hyperledger.ariesframework.credentials.utils

import org.hyperledger.ariesframework.anoncreds.utils.AnonCredsEncoder
import org.hyperledger.ariesframework.util.LogUtil

class EncodeHelper {
    companion object {
        data class EncodeTemp(
            val referent: String,
            val raw: Any?,
            val encoded: String,
            val expected: String,
        )

        fun logEncodingSample(tag: String, whenLabel: String, sample: EncodeTemp) {
            LogUtil.info(this) { "==== $whenLabel ($tag) ====" }
            LogUtil.info(this) { "atributo : ${sample.referent}" }
            LogUtil.info(this) { "valor do atributo : ${sample.raw}" }
            LogUtil.info(this) { "encoded recebido : ${sample.encoded}" }
            LogUtil.info(this) { "encoded esperado : ${sample.expected}" }
            LogUtil.info(this) { "match? : ${sample.expected == sample.encoded}" }
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

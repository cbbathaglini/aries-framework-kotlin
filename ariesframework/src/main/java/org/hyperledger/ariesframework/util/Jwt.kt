package org.hyperledger.ariesframework.util
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import org.hyperledger.ariesframework.error.CredoError

class Jwt private constructor(
    val payload: JwtPayload,
    val header: JwtHeader,
    val signature: ByteArray,
    val serializedJwt: String
) {

    companion object {
        private val format = Regex("""^[A-Za-z0-9\-_]+=*\.[A-Za-z0-9\-_]+=*\.[A-Za-z0-9\-_+/=]*$""")

        fun fromSerializedJwt(serializedJwt: String): Jwt {
            if (!format.matches(serializedJwt)) {
                throw CredoError("Invalid JWT. '$serializedJwt' does not match JWT regex")
            }

            val parts = serializedJwt.split('.')
            if (parts.size < 2) throw CredoError("JWT must have at least header and payload")

            val headerJson = JsonEncoder.fromBase64(parts[0])
            val payloadJson = JsonEncoder.fromBase64(parts[1])
            val signatureBytes = if (parts.size > 2) {
                TypedArrayEncoder.fromBase64(parts[2])
            } else ByteArray(0)

            return Jwt(
                payload = JwtPayload.fromJson(payloadJson),
                header = JwtHeader(
                    alg = headerJson["alg"] as String,
                    kid = headerJson["kid"] as? String,
                    jwk = (headerJson["jwk"] as? Map<String, Any?>)?.let { Jwk.fromJson(it) },
                    x5c = (headerJson["x5c"] as? List<*>)?.filterIsInstance<String>(),
                    additionalProperties = headerJson.filterKeys {
                        it !in listOf("alg", "kid", "jwk", "x5c")
                    }
                ),
                signature = signatureBytes,
                serializedJwt = serializedJwt
            )
        }
    }
}

data class JwtPayload(val sub: String?, val iss: String?, val exp: Long?) {
    companion object {
        fun fromJson(json: Map<String, Any?>): JwtPayload {
            return JwtPayload(
                sub = json["sub"] as? String,
                iss = json["iss"] as? String,
                exp = (json["exp"] as? Number)?.toLong()
            )
        }
    }
}

@Serializable
data class JwtHeader(
    val alg: String,
    val kid: String? = null,
    val jwk: Jwk? = null,
    val x5c: List<String>? = null,
    val additionalProperties: Map<String, Any?> = emptyMap()
)
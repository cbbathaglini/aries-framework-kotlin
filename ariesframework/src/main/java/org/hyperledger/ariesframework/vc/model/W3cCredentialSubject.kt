import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class W3cCredentialSubject(
    val id: String? = null,

    @SerialName("claims")
    val claims: Map<String, JsonElement>? = null
) {
    companion object {
        fun fromClaims(claimsObject: JsonObject?): W3cCredentialSubject {
            if (claimsObject == null) return W3cCredentialSubject()

            val idElement = claimsObject["id"]
            val id = idElement?.toString()?.trim('"')

            val filteredClaims = claimsObject
                .filterKeys { it != "id" }
                .takeIf { it.isNotEmpty() }

            return W3cCredentialSubject(
                id = id,
                claims = filteredClaims
            )
        }
    }
}
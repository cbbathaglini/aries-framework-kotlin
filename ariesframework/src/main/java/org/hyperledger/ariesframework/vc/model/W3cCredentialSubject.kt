import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.hyperledger.ariesframework.vc.model.W3cCredentialSubjectSerializer

@Serializable(with = W3cCredentialSubjectSerializer::class)
data class W3cCredentialSubject(
    @SerialName("@id")
    val id: String? = null,

    @SerialName("claims")
    val claims: Map<String, JsonElement>? = emptyMap(),
)

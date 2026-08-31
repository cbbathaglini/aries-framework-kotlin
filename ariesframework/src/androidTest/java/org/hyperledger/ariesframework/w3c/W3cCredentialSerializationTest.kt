package org.hyperledger.ariesframework.w3c

import W3cCredentialSubject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.vc.model.W3cCredential
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class W3cCredentialSerializationTest {

    @Test
    fun testW3cCredentialToJson() {
        val subject = W3cCredentialSubject(
            id = "did:example:holder",
            claims = mapOf(
                "name" to JsonPrimitive("Alice Smith"),
                "degree" to JsonPrimitive("Bachelor of Computer Science"),
            ),
        )
        val credential = W3cCredential(
            context = listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1")),
            id = "http://example.edu/credentials/3732",
            type = listOf("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = JsonPrimitive("did:example:issuer"),
            issuanceDate = "2010-01-01T19:23:24Z",
            credentialSubject = listOf(subject),
        )

        val json = credential.toJson()
        val parsed = Json.parseToJsonElement(json).jsonObject

        assertEquals("http://example.edu/credentials/3732", parsed["id"]?.jsonPrimitive?.content)
        assertEquals("did:example:issuer", parsed["issuer"]?.jsonPrimitive?.content)
        assertEquals(2, (parsed["type"] as JsonArray).size)
        assertEquals(1, (parsed["credentialSubject"] as JsonArray).size)
        assertEquals(
            "did:example:holder",
            (parsed["credentialSubject"] as JsonArray).first().jsonObject["id"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun testW3cCredentialFromJson() {
        val json = """
        {
            "@context": ["https://www.w3.org/2018/credentials/v1"],
            "id": "http://example.edu/credentials/3732",
            "type": ["VerifiableCredential", "UniversityDegreeCredential"],
            "issuer": "did:example:issuer",
            "issuanceDate": "2010-01-01T19:23:24Z",
            "credentialSubject": [
                {
                    "id": "did:example:holder",
                    "degree": {
                        "type": "BachelorDegree",
                        "name": "Bachelor of Science"
                    }
                }
            ]
        }
        """.trimIndent()

        val credential = W3cCredential.fromJson(json)

        assertEquals(listOf("VerifiableCredential", "UniversityDegreeCredential"), credential.type)
        assertEquals("did:example:issuer", credential.issuer.jsonPrimitive.content)
        assertEquals("http://example.edu/credentials/3732", credential.id)
        assertEquals(1, credential.credentialSubject.size)
        assertEquals("did:example:holder", credential.credentialSubject.first().id)
        assertNotNull(credential.credentialSubject.first().claims)
    }

    @Test
    fun testW3cCredentialNormalizeIncomingPayload() {
        // credentialSubject as a single object (not array), type as single string
        val json = """
        {
            "format": "ldp_vc",
            "credential": {
                "@context": "https://www.w3.org/2018/credentials/v1",
                "id": "http://example.edu/credentials/27856",
                "type": "VerifiableCredential",
                "issuer": "did:example:issuer",
                "issuanceDate": "2010-01-01T19:23:24Z",
                "credentialSubject": {
                    "id": "did:example:holder",
                    "name": "Alice Smith"
                }
            }
        }
        """.trimIndent()

        val credential = W3cCredential.fromJsonOb(json)

        // Unwrapped from "credential" key
        assertEquals("http://example.edu/credentials/27856", credential.id)
        // type converted to array
        assertEquals(listOf("VerifiableCredential"), credential.type)
        // credentialSubject converted to array
        assertEquals(1, credential.credentialSubject.size)
        // the normalize step converts "id" -> "@id"; the custom serializer reads the "id" key
        val claims = credential.credentialSubject.first().claims
        assertNotNull(claims)
        assertEquals("Alice Smith", claims?.get("name")?.jsonPrimitive?.content)
    }

    @Test
    fun testW3cCredentialSubjectSerialization() {
        val subject = W3cCredentialSubject(
            id = "did:example:holder",
            claims = mapOf("age" to JsonPrimitive(30)),
        )

        val json = Json.encodeToString(W3cCredentialSubject.serializer(), subject)
        val parsed = Json.parseToJsonElement(json).jsonObject

        assertEquals("did:example:holder", parsed["id"]?.jsonPrimitive?.content)
        assertEquals(30, parsed["age"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun testW3cJsonLdVerifiableCredentialRoundTrip() {
        val context: List<kotlinx.serialization.json.JsonElement> = listOf(
            JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
            JsonPrimitive("https://www.w3.org/2018/credentials/examples/v1"),
        )
        val credential = W3cJsonLdVerifiableCredential(
            context = context,
            id = "http://example.edu/credentials/1872",
            type = listOf("VerifiableCredential", "UniversityDegreeCredential"),
            issuer = JsonPrimitive("did:example:issuer"),
            issuanceDate = "2010-01-01T19:23:24Z",
            credentialSubject = listOf(
                W3cCredentialSubject(
                    id = "did:example:holder",
                    claims = mapOf("name" to JsonPrimitive("Alice Smith")),
                ),
            ),
        )

        val json = credential.toJson()
        val parsed = Json.parseToJsonElement(json).jsonObject
        assertEquals("http://example.edu/credentials/1872", parsed["id"]?.jsonPrimitive?.content)
        assertEquals(2, (parsed["@context"] as JsonArray).size)
        assertEquals(1, (parsed["credentialSubject"] as JsonArray).size)

        val decoded = W3cJsonLdVerifiableCredential.fromJson(json)
        assertEquals(credential.id, decoded.id)
        assertEquals(credential.type, decoded.type)
        assertEquals(credential.credentialSubject.first().id, decoded.credentialSubject.first().id)
    }

    @Test
    fun testW3cCredentialWithSchemaAndStatus() {
        val context: List<kotlinx.serialization.json.JsonElement> = listOf(
            JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
        )
        val credential = W3cCredential(
            context = context,
            id = "http://example.edu/credentials/9999",
            type = listOf("VerifiableCredential"),
            issuer = JsonPrimitive("did:example:issuer"),
            issuanceDate = "2020-01-01T00:00:00Z",
            credentialSubject = listOf(
                W3cCredentialSubject(id = "did:example:holder", claims = emptyMap()),
            ),
            credentialSchema = listOf(
                org.hyperledger.ariesframework.vc.model.W3cCredentialSchema(
                    id = "http://example.org/schemas/123",
                    type = "JsonSchema",
                ),
            ),
            credentialStatus = org.hyperledger.ariesframework.vc.model.W3cCredentialStatus(
                id = "http://example.org/status/42",
                type = "RevocationList2021Status",
            ),
        )

        val json = credential.toJson()
        val parsed = Json.parseToJsonElement(json).jsonObject

        assertEquals(1, (parsed["credentialSchema"] as JsonArray).size)
        assertEquals(
            "http://example.org/schemas/123",
            (parsed["credentialSchema"] as JsonArray).first().jsonObject["id"]?.jsonPrimitive?.content,
        )
        assertEquals("RevocationList2021Status", parsed["credentialStatus"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun testOptionalFieldsDefaultToNull() {
        val context: List<kotlinx.serialization.json.JsonElement> = listOf(
            JsonPrimitive("https://www.w3.org/2018/credentials/v1"),
        )
        val credential = W3cCredential(
            context = context,
            type = listOf("VerifiableCredential"),
            issuer = JsonPrimitive("did:example:issuer"),
            issuanceDate = "2020-01-01T00:00:00Z",
            credentialSubject = listOf(
                W3cCredentialSubject(claims = mapOf("name" to JsonPrimitive("No Id Holder"))),
            ),
        )

        assertNull(credential.id)
        assertNull(credential.expirationDate)
        assertNull(credential.credentialStatus)
        assertNull(credential.credentialSubject.first().id)
        assertEquals(emptyList<org.hyperledger.ariesframework.vc.model.W3cCredentialSchema>(), credential.credentialSchema)
    }
}

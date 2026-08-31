package org.hyperledger.ariesframework.w3c

import W3cCredentialSubject
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.hyperledger.ariesframework.TestHelper
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.vc.model.W3cCredential
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class W3cCredentialServiceTest {
    lateinit var agent: Agent

    @Before
    fun setUp() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = TestHelper.getBaseConfig("w3c")
        agent = Agent(context, config)
        agent.initialize()
    }

    @After
    fun tearDown() = runTest {
        agent.reset()
    }

    private fun buildJsonLdCredential(
        id: String = "http://example.edu/credentials/3732",
        subjectId: String = "did:example:holder",
    ): W3cJsonLdVerifiableCredential {
        return W3cJsonLdVerifiableCredential(
            context = listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1")),
            id = id,
            type = listOf("VerifiableCredential"),
            issuer = JsonPrimitive("did:example:issuer"),
            issuanceDate = "2010-01-01T19:23:24Z",
            credentialSubject = listOf(
                W3cCredentialSubject(
                    id = subjectId,
                    claims = mapOf("name" to JsonPrimitive("Alice Smith")),
                ),
            ),
        )
    }

    @Test
    fun testStoreCredentialW3cJsonLdVerifiableCredential() = runTest {
        val credential = buildJsonLdCredential()
        val record = agent.w3cCredentialService.storeCredentialW3cJsonLdVerifiableCredential(credential)

        assertNotNull(record.id)
        assertEquals(credential.id, record.credential.id)
        assertEquals(credential.type, record.credential.type)
        assertTrue(record.getTags().containsKey("type"))
    }

    @Test
    fun testStoreCredentialW3cCredential() = runTest {
        val subject = W3cCredentialSubject(
            id = "did:example:holder",
            claims = mapOf("name" to JsonPrimitive("Bob Builder")),
        )
        val credential = W3cCredential(
            context = listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1")),
            id = "http://example.edu/credentials/999",
            type = listOf("VerifiableCredential"),
            issuer = JsonPrimitive("did:example:issuer"),
            issuanceDate = "2020-01-01T00:00:00Z",
            credentialSubject = listOf(subject),
        )

        val record = agent.w3cCredentialService.storeCredentialW3cCredential(credential)

        assertNotNull(record.id)
        assertEquals(credential.id, record.credential.id)
        assertEquals("did:example:holder", record.credential.credentialSubject.first().id)
    }

    @Test
    fun testProcessAndStoreW3cCredential() = runTest {
        val rawJson = """
        {
            "format": "ldp_vc",
            "credential": {
                "@context": ["https://www.w3.org/2018/credentials/v1"],
                "id": "http://example.edu/credentials/1872",
                "type": "VerifiableCredential",
                "issuer": "did:example:issuer",
                "issuanceDate": "2010-01-01T19:23:24Z",
                "credentialSubject": {
                    "name": "Carol"
                }
            }
        }
        """.trimIndent()

        val credential = agent.w3cCredentialService.processAndStorew3cCredential(rawJson)

        assertEquals("http://example.edu/credentials/1872", credential.id)
        assertEquals(listOf("VerifiableCredential"), credential.type)
        assertEquals(1, credential.credentialSubject.size)
    }

    @Test
    fun testFindByCredentialSubjectId() = runTest {
        val subjectId = "did:example:alice"
        agent.w3cCredentialService.storeCredentialW3cJsonLdVerifiableCredential(
            buildJsonLdCredential(id = "http://example.edu/credentials/1", subjectId = subjectId),
        )
        agent.w3cCredentialService.storeCredentialW3cJsonLdVerifiableCredential(
            buildJsonLdCredential(id = "http://example.edu/credentials/2", subjectId = "did:example:bob"),
        )

        val records = agent.w3cCredentialService.findByCredentialSubjectId(subjectId)

        assertEquals(1, records.size)
        assertEquals("http://example.edu/credentials/1", records.first().credential.id)
        assertEquals(subjectId, records.first().credential.credentialSubject.first().id)
    }

    @Test
    fun testGetExpandedTypesForCredential() = runTest {
        val context = listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))
        val types = listOf("VerifiableCredential")

        val expanded = agent.w3cJsonLdCredentialService.getExpandedTypesForCredential(context, types)

        val expandedTypes = expanded["type"]
        assertNotNull(expandedTypes)
        assertTrue(expandedTypes!!.contains("https://www.w3.org/2018/credentials#VerifiableCredential"))
    }

    @Test
    fun testResetDeletesW3cCredentials() = runTest {
        agent.w3cCredentialService.storeCredentialW3cJsonLdVerifiableCredential(
            buildJsonLdCredential(subjectId = "did:example:temp"),
        )

        val before = agent.w3cCredentialService.findByCredentialSubjectId("did:example:temp")
        assertEquals(1, before.size)

        agent.reset()
        agent.initialize()

        val after = agent.w3cCredentialService.findByCredentialSubjectId("did:example:temp")
        assertTrue(after.isEmpty())
    }
}

package org.hyperledger.ariesframework.webvh

import com.google.gson.JsonParser
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.Ed25119Sig2018
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebVhDidResolverTest {

    private val resolver = WebVhDidResolver()
    private val jsonParser = JsonParser()

    @Test
    fun testParseDidDocumentFromExplorer() {
        val jsonString = """
        {
            "@context": ["https://www.w3.org/ns/did/v1", "https://www.w3.org/ns/cid/v1"],
            "id": "did:webvh:QmVn5rY71EWXeJDHMcwwDojEiULPvQbhdFJxqPC2vjyzHh:webvh.example.com:ns-01:d447dd",
            "verificationMethod": [
                {
                    "id": "did:webvh:QmVn5rY71EWXeJDHMcwwDojEiULPvQbhdFJxqPC2vjyzHh:webvh.example.com:ns-01:d447dd#z6Mksibjgh4HDo1xv8wq4vWrZMnKxfSGKKeKM8XQzCrVPavp",
                    "type": "Multikey",
                    "controller": "did:webvh:QmVn5rY71EWXeJDHMcwwDojEiULPvQbhdFJxqPC2vjyzHh:webvh.example.com:ns-01:d447dd",
                    "publicKeyMultibase": "z6Mksibjgh4HDo1xv8wq4vWrZMnKxfSGKKeKM8XQzCrVPavp"
                }
            ],
            "authentication": [
                "did:webvh:QmVn5rY71EWXeJDHMcwwDojEiULPvQbhdFJxqPC2vjyzHh:webvh.example.com:ns-01:d447dd#z6Mksibjgh4HDo1xv8wq4vWrZMnKxfSGKKeKM8XQzCrVPavp"
            ],
            "assertionMethod": [
                "did:webvh:QmVn5rY71EWXeJDHMcwwDojEiULPvQbhdFJxqPC2vjyzHh:webvh.example.com:ns-01:d447dd#z6Mksibjgh4HDo1xv8wq4vWrZMnKxfSGKKeKM8XQzCrVPavp"
            ],
            "service": []
        }
        """.trimIndent()

        val didDocumentJson = jsonParser.parse(jsonString).asJsonObject
        val didDoc = resolver.parseDidDocument(didDocumentJson)

        assertEquals(
            "did:webvh:QmVn5rY71EWXeJDHMcwwDojEiULPvQbhdFJxqPC2vjyzHh:webvh.example.com:ns-01:d447dd",
            didDoc.id,
        )
        Assert.assertEquals(1, didDoc.publicKey.size)
        Assert.assertEquals(1, didDoc.authentication.size)
        assertTrue(didDoc.service.isEmpty())

        val pk = didDoc.publicKey.first() as Ed25119Sig2018
        assertTrue(pk.id.contains("z6Mksibjgh4HDo1xv8wq4vWrZMnKxfSGKKeKM8XQzCrVPavp"))
    }

    @Test
    fun testParseDidDocumentWithServices() {
        val jsonString = """
        {
            "@context": ["https://www.w3.org/ns/did/v1"],
            "id": "did:webvh:QmSCID:example.com:issuer:alice",
            "verificationMethod": [
                {
                    "id": "did:webvh:QmSCID:example.com:issuer:alice#key-1",
                    "type": "Multikey",
                    "controller": "did:webvh:QmSCID:example.com:issuer:alice",
                    "publicKeyMultibase": "z6Mksibjgh4HDo1xv8wq4vWrZMnKxfSGKKeKM8XQzCrVPavp"
                }
            ],
            "authentication": ["did:webvh:QmSCID:example.com:issuer:alice#key-1"],
            "service": [
                {
                    "id": "did:webvh:QmSCID:example.com:issuer:alice#didcomm",
                    "type": "DIDCommMessaging",
                    "serviceEndpoint": {
                        "uri": "https://agent.example.com/didcomm",
                        "routingKeys": ["did:key:z6MkrBmbpSbSMFVU4LKVgNgZqVsDzpP6t92C6Q1M91rVaJ7z"],
                        "accept": ["didcomm/v2"]
                    }
                },
                {
                    "id": "did:webvh:QmSCID:example.com:issuer:alice#indy",
                    "type": "did-communication",
                    "serviceEndpoint": "https://agent.example.com/msg",
                    "recipientKeys": ["GMm4vqdw7w8fJNXumV3P34LkVNPGc9vextXtBjbUMkak"],
                    "routingKeys": ["A4rG8WJ8eXj3KQVFPpHM1V9YAoGAQPJVcrxtMYKmmGjx"]
                }
            ]
        }
        """.trimIndent()

        val didDocumentJson = jsonParser.parse(jsonString).asJsonObject
        val didDoc = resolver.parseDidDocument(didDocumentJson)

        assertEquals("did:webvh:QmSCID:example.com:issuer:alice", didDoc.id)
        Assert.assertEquals(1, didDoc.publicKey.size)
        Assert.assertEquals(1, didDoc.authentication.size)
        Assert.assertEquals(2, didDoc.service.size)
    }
}

package org.hyperledger.ariesframework.webvh

import com.google.gson.JsonObject
import io.github.decentralizedidentity.didwebvh.core.DidWebVh
import org.hyperledger.ariesframework.connection.models.didauth.DidCommService
import org.hyperledger.ariesframework.connection.models.didauth.DidCommV2Service
import org.hyperledger.ariesframework.connection.models.didauth.DidDoc
import org.hyperledger.ariesframework.connection.models.didauth.DidDocService
import org.hyperledger.ariesframework.connection.models.didauth.ReferencedAuthentication
import org.hyperledger.ariesframework.connection.models.didauth.ServiceEndpoint
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.Ed25119Sig2018
import org.hyperledger.ariesframework.connection.models.didauth.publicKey.PublicKey
import org.hyperledger.ariesframework.util.Base58
import org.slf4j.LoggerFactory

class WebVhDidResolver {

    private val logger = LoggerFactory.getLogger(WebVhDidResolver::class.java)

    suspend fun resolve(did: String): DidDoc {
        logger.debug("Resolving did:webvh DID: $did")

        val resolveResult = DidWebVh.resolve(did)
        val didDocumentJson = resolveResult.didDocument.asJsonObject()

        return parseDidDocument(didDocumentJson)
    }

    fun parseDidDocument(didDocumentJson: JsonObject): DidDoc {
        val publicKeys = extractPublicKeys(didDocumentJson)
        val authentications = extractAuthentication(didDocumentJson)
        val services = extractServices(didDocumentJson)

        val didDoc = DidDoc(
            id = didDocumentJson.get("id").asJsonPrimitive.asString,
            publicKey = publicKeys,
            authentication = authentications,
            service = services,
        )
        logger.debug("Parsed did:webvh DID document: $didDoc")
        return didDoc
    }

    private fun extractPublicKeys(didDocumentJson: JsonObject): List<PublicKey> {
        val publicKeys = mutableListOf<PublicKey>()
        val verificationMethod = didDocumentJson.getAsJsonArray(VERIFICATION_METHOD) ?: return publicKeys
        for (i in 0 until verificationMethod.size()) {
            val vm = verificationMethod[i].asJsonObject
            val vmId = vm.get("id").asJsonPrimitive.asString
            val controller = vm.get("controller").asJsonPrimitive.asString
            val type = vm.get("type").asJsonPrimitive.asString

            if (type in SUPPORTED_KEY_TYPES) {
                val publicKeyBase58 = vm.get(PUBLIC_KEY_BASE58)?.asJsonPrimitive?.asString
                val publicKeyMultibase = vm.get(PUBLIC_KEY_MULTIBASE)?.asJsonPrimitive?.asString

                val verkey = when {
                    publicKeyBase58 != null -> publicKeyBase58
                    publicKeyMultibase != null -> multibaseToBase58(publicKeyMultibase)
                    else -> continue
                }
                publicKeys.add(Ed25119Sig2018(id = vmId, controller = controller, publicKeyBase58 = verkey))
            }
        }
        return publicKeys
    }

    private fun extractAuthentication(didDocumentJson: JsonObject): List<org.hyperledger.ariesframework.connection.models.didauth.Authentication> {
        val authentications = mutableListOf<org.hyperledger.ariesframework.connection.models.didauth.Authentication>()
        val authentication = didDocumentJson.getAsJsonArray(AUTHENTICATION) ?: return authentications
        for (i in 0 until authentication.size()) {
            val element = authentication[i]
            if (element.isJsonPrimitive) {
                authentications.add(ReferencedAuthentication(type = ED25519_VERIFICATION_KEY_2018, publicKey = element.asJsonPrimitive.asString))
            }
        }
        return authentications
    }

    private fun extractServices(didDocumentJson: JsonObject): List<DidDocService> {
        val services = mutableListOf<DidDocService>()
        val serviceArray = didDocumentJson.getAsJsonArray(SERVICE) ?: return services
        for (i in 0 until serviceArray.size()) {
            val svc = serviceArray[i].asJsonObject
            val svcId = svc.get("id").asJsonPrimitive.asString
            val svcType = svc.get("type").asJsonPrimitive.asString

            when (svcType) {
                DID_COMM_MESSAGING, DID_COMM -> {
                    val endpoint = parseServiceEndpoint(svc)
                    if (endpoint != null) {
                        services.add(DidCommV2Service(id = svcId, serviceEndpoint = endpoint))
                    }
                }
                DID_COMMUNICATION, INDY_AGENT -> {
                    val endpoint = svc.get(SERVICE_ENDPOINT).asJsonPrimitive.asString
                    val recipientKeys = parseStringArray(svc, RECIPIENT_KEYS)
                    val routingKeys = parseStringArray(svc, ROUTING_KEYS)
                    services.add(DidCommService(id = svcId, serviceEndpoint = endpoint, recipientKeys = recipientKeys, routingKeys = routingKeys))
                }
            }
        }
        return services
    }

    private fun parseServiceEndpoint(svc: JsonObject): ServiceEndpoint? {
        val endpointElement = svc.get(SERVICE_ENDPOINT) ?: return null
        if (endpointElement.isJsonPrimitive) {
            return ServiceEndpoint(uri = endpointElement.asJsonPrimitive.asString)
        }
        if (endpointElement.isJsonObject) {
            val obj = endpointElement.asJsonObject
            return ServiceEndpoint(
                uri = obj.get("uri").asJsonPrimitive.asString,
                routingKeys = parseStringArray(obj, ROUTING_KEYS),
                accept = parseStringArray(obj, ACCEPT),
            )
        }
        return null
    }

    private fun parseStringArray(obj: JsonObject, key: String): List<String> {
        val array = obj.getAsJsonArray(key) ?: return emptyList()
        return (0 until array.size()).map { array[it].asJsonPrimitive.asString }
    }

    private fun multibaseToBase58(multibase: String): String {
        val raw = multibase.drop(1)
        val bytes = Base58.decode(raw)
        return if (bytes.size > 32) Base58.encode(bytes.takeLast(32).toByteArray()) else raw
    }

    companion object {
        val supportedMethods = listOf("webvh")

        private const val VERIFICATION_METHOD = "verificationMethod"
        private const val AUTHENTICATION = "authentication"
        private const val SERVICE = "service"
        private const val SERVICE_ENDPOINT = "serviceEndpoint"
        private const val PUBLIC_KEY_BASE58 = "publicKeyBase58"
        private const val PUBLIC_KEY_MULTIBASE = "publicKeyMultibase"
        private const val RECIPIENT_KEYS = "recipientKeys"
        private const val ROUTING_KEYS = "routingKeys"
        private const val ACCEPT = "accept"

        private const val DID_COMM_MESSAGING = "DIDCommMessaging"
        private const val DID_COMM = "DIDComm"
        private const val DID_COMMUNICATION = "did-communication"
        private const val INDY_AGENT = "IndyAgent"
        private const val ED25519_VERIFICATION_KEY_2018 = "Ed25519VerificationKey2018"

        private val SUPPORTED_KEY_TYPES = setOf("Multikey", "Ed25519VerificationKey2018", "Ed25519VerificationKey2020")
    }
}

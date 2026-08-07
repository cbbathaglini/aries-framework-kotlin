package org.hyperledger.ariesframework.webvh

import org.hyperledger.ariesframework.util.DIDParser
import org.slf4j.LoggerFactory

class DidResolverService {

    private val logger = LoggerFactory.getLogger(DidResolverService::class.java)
    private val webvhResolver = WebVhDidResolver()

    suspend fun resolve(did: String) = webvhResolver.resolve(did)

    fun supports(did: String): Boolean {
        return try {
            DIDParser.getMethod(did) in WebVhDidResolver.supportedMethods
        } catch (e: Exception) {
            false
        }
    }
}

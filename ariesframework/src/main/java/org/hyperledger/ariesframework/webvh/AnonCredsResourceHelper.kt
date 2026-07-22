package org.hyperledger.ariesframework.webvh

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.decentralizedidentity.didwebvh.core.url.DidToHttpsTransformer
import io.github.decentralizedidentity.didwebvh.core.url.DidWebVhUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

class AnonCredsResourceHelper {

    private val logger = LoggerFactory.getLogger(AnonCredsResourceHelper::class.java)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchResource(did: String, resourceId: String, emulator: Boolean = false): JsonObject {
        val httpsBase = didToUrl(did, emulator)
        val resourceUrl = "$httpsBase/resources/$resourceId"

        logger.info("Fetching attested resource from: $resourceUrl")

        val request = Request.Builder()
            .url(resourceUrl)
            .get()
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw RuntimeException("Failed to fetch resource $resourceId from $resourceUrl: HTTP ${response.code}")
        }

        val body = response.body?.string() ?: throw RuntimeException("Empty response body for resource $resourceId")
        return JsonParser.parseString(body).asJsonObject
    }

    suspend fun fetchResourceByIdentifier(identifier: String, emulator: Boolean = false): JsonObject {
        logger.info("Fetching resource by identifier: $identifier")
        val parts = identifier.split("/resources/")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid did:webvh resource identifier: $identifier")
        }
        val did = parts[0]
        val resourceId = parts[1]
        logger.info("Parsed identifier - DID: $did, resourceId: $resourceId")
        return fetchResource(did, resourceId, emulator)
    }

    private fun didToUrl(did: String, emulator: Boolean): String {
        return try {
            DidToHttpsTransformer.toHttpsUrl(did).trimEnd('/')
        } catch (e: Exception) {
            logger.info("DidToHttpsTransformer failed: ${e.message}")
            WebVhEmulatorWorkaround.buildBaseUrl(did, emulator) ?: throw e
        }
    }
}

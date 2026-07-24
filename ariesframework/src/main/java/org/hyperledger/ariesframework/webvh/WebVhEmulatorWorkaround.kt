package org.hyperledger.ariesframework.webvh

import org.hyperledger.ariesframework.agent.Agent
import org.slf4j.LoggerFactory
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Properties

/**
 * Workaround for did:webvh development resources.
 *
 * Remove this file and the calls to it when local IP-based WebVH testing is no longer needed.
 */
object WebVhEmulatorWorkaround {

    private val logger = LoggerFactory.getLogger(WebVhEmulatorWorkaround::class.java)

    fun isEnabled(agent: Agent): Boolean {
        val assetFileName = agent.agentConfig.cacheConfigFile ?: CONFIG_PROPERTIES
        val props = Properties()
        return try {
            agent.context.assets.open(assetFileName).use { props.load(it) }
            props.getProperty(EMULATOR_PROPERTY, "false").trim().equals("true", ignoreCase = true)
        } catch (e: Exception) {
            logger.debug("Could not load $assetFileName to read $EMULATOR_PROPERTY. Using false.", e)
            false
        }
    }

    fun buildBaseUrl(did: String, enabled: Boolean): String? {
        if (!enabled) return null

        val parts = did.removePrefix("did:webvh:").split(":")
        logger.info("Building emulator URL manually - did parts: $parts")
        if (parts.size < 3) {
            throw IllegalArgumentException("Invalid did:webvh DID format: $did")
        }

        val domain = parts[1]
        val mappedDomain = mapDomain(domain)
        val path = parts.drop(2).joinToString("/")
        val url = "http://$mappedDomain:$WEBVH_LOCAL_PORT/$path"
        logger.info("Emulator URL - original domain: $domain, mapped: $mappedDomain, URL: $url")
        return url
    }

    private fun mapDomain(domain: String): String {
        return if (isLocalIp(domain)) ANDROID_EMULATOR_HOST else domain
    }

    private fun isLocalIp(ip: String): Boolean {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && addr.hostAddress == ip) return true
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to enumerate network interfaces", e)
        }
        return false
    }

    private const val CONFIG_PROPERTIES = "config.properties"
    private const val EMULATOR_PROPERTY = "emulator"
    private const val ANDROID_EMULATOR_HOST = "10.0.2.2"
    private const val WEBVH_LOCAL_PORT = 8000
}

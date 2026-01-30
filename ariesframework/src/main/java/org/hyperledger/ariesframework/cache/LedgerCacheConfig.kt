package org.hyperledger.ariesframework.cache

import android.content.Context
import org.slf4j.LoggerFactory
import java.util.Properties

data class LedgerCacheConfig(
    val credDefTtlDaysById: Map<String, Long>,
    val credDefDefaultDays: Long,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(LedgerCacheConfig::class.java)

        fun load(
            context: Context,
            assetFileName: String = "config.properties",
        ): LedgerCacheConfig {

            val props = Properties()

            try {
                context.assets.open(assetFileName).use { props.load(it) }
                logger.info("[CACHE CONFIG] Loaded $assetFileName from assets")
            } catch (e: Throwable) {
                logger.warn(
                    "[CACHE CONFIG] Could not load $assetFileName. Using defaults. err=${e.message}"
                )
            }

            val defaultDays =
                props.getProperty("cache.default.ttl.days", "1")
                    .trim()
                    .toLongOrNull()
                    ?: 1L // default 1 day

            val rawMap =
                props.getProperty("creddef.ttl.map", "")
                    .trim()

            val parsedMap = parseCredDefTtlMap(rawMap)

            logger.info(
                "[CACHE CONFIG] defaultDays=$defaultDays | credDefOverrides=${parsedMap.size}"
            )

            return LedgerCacheConfig(
                credDefTtlDaysById = parsedMap,
                credDefDefaultDays = defaultDays,
            )
        }

        private fun parseCredDefTtlMap(raw: String): Map<String, Long> {
            if (raw.isBlank()) return emptyMap()

            return raw
                .split(";")
                .asSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { entry ->
                    val parts = entry.split(",", limit = 2)
                        .map { it.trim() }

                    if (parts.size != 2) {
                        logger.warn("[CACHE CONFIG] Invalid ttl entry ignored: '$entry'")
                        return@mapNotNull null
                    }

                    val credDefId = parts[0]
                    val days = parts[1].toLongOrNull()

                    if (days == null || days <= 0) {
                        logger.warn("[CACHE CONFIG] Invalid ttl days for '$credDefId': '${parts[1]}'")
                        return@mapNotNull null
                    }

                    credDefId to days
                }
                .toMap()
        }
    }
}
package org.hyperledger.ariesframework.cache

import android.content.Context

class LedgerCacheProperties(context: Context) {
    private val cfg: LedgerCacheConfig = LedgerCacheConfig.load(context, "config.properties")

    fun defaultTtlDays(): Long = cfg.credDefDefaultDays
}

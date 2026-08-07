package org.hyperledger.ariesframework.webvh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebVhAnonCredsRegistryTest {

    private val registry = WebVhAnonCredsRegistry()

    @Test
    fun testSupportedIdentifierMatchesDidWebvh() {
        assertTrue(registry.supportedIdentifier.matches("did:webvh:QmSCID:domain:ns:alias/resources/QmHash123"))
        assertTrue(registry.supportedIdentifier.matches("did:webvh:QmSCID:domain"))
        assertFalse(registry.supportedIdentifier.matches("did:ethr:0x123"))
        assertFalse(registry.supportedIdentifier.matches("did:peer:123"))
    }
}

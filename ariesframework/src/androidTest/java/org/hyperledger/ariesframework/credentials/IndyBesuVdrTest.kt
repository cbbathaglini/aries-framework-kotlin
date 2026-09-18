package org.hyperledger.ariesframework.credentials

import indy_besu_vdr.LedgerConfiguration
import indy_besu_vdr.LedgerRouter
import indy_besu_vdr.revocationStatusListFromString
import indy_besu_vdr.revocationStatusListToString
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList
import org.hyperledger.ariesframework.ledger.ledgerBesu.BesuVdr
import org.hyperledger.ariesframework.util.serializer.RevocationStatusListSerializer
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Exercises the published native library without requiring a running ledger. */
class IndyBesuVdrTest {
    @Before
    fun configureNativeLibrary() {
        BesuVdr.configureNativeLibrary()
    }

    @Test
    fun revocationStatusListPreservesNativeAndFrameworkJson() {
        val source = """
            {
                "issuerId": "did:ethr:testnet:0x0000000000000000000000000000000000000001",
                "revRegDefId": "did:ethr:testnet:0x0000000000000000000000000000000000000001/anoncreds/v0/REV_REG_DEF/test",
                "timestamp": 1720000000,
                "revocationList": [0, 1, 0],
                "currentAccumulator": "test-accumulator"
            }
        """.trimIndent()

        val status = revocationStatusListFromString(source)
        assertEquals(1720000000uL, status.timestamp)
        assertEquals(listOf(0u, 1u, 0u), status.revocationList)
        assertEquals(
            Json.parseToJsonElement(source),
            Json.parseToJsonElement(revocationStatusListToString(status)),
        )

        val encoded = Json.encodeToString(RevocationStatusListSerializer, status)
        assertEquals(Json.parseToJsonElement(source), Json.parseToJsonElement(encoded))
        assertEquals(
            AnonCredsRevocationStatusList.toAnonCreds(status),
            Json.decodeFromString<AnonCredsRevocationStatusList>(encoded),
        )
    }

    @Test
    fun ledgerRouterSelectsConfiguredNetwork() {
        val router = LedgerRouter(
            listOf(
                LedgerConfiguration(
                    chainId = 1337uL,
                    nodeAddress = "http://127.0.0.1:8545",
                    contractConfigs = emptyList(),
                    network = "testnet",
                    quorumConfig = null,
                ),
            ),
        )
        try {
            assertEquals(listOf("testnet"), router.listNetworks())
            val client = router.getLedgerForIdentifier(
                "did:ethr:testnet:0x0000000000000000000000000000000000000001",
            )
            try {
                assertEquals("testnet", client.network())
            } finally {
                client.close()
            }
        } finally {
            router.close()
        }
    }
}

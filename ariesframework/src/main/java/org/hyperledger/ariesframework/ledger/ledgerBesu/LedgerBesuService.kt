package org.hyperledger.ariesframework.ledger.ledgerBesu

import ILedgerService
import anoncreds_uniffi.Issuer
import indy_vdr_uniffi.Ledger
import indy_vdr_uniffi.Pool
import indy_vdr_uniffi.openPool
import indy_vdr_uniffi.setProtocolVersion
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.RevocationRegistryDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.wallet.DidInfo
import org.slf4j.LoggerFactory

class LedgerBesuService(val agent: Agent) : ILedgerService {
    private val logger = LoggerFactory.getLogger(LedgerBesuService::class.java)
    private var pool: Pool? = null
    private val ledger = Ledger()
    private val issuer = Issuer()
    private val jsonIgnoreUnknown = Json { ignoreUnknownKeys = true }

    override suspend fun initialize() {
        logger.info("Initializing Pool")
        if (pool != null) {
            logger.warn("Pool already initialized.")
            return
        }

        setProtocolVersion(2)
        try {
            pool = openPool(agent.agentConfig.genesisPath, null, null)
        } catch (e: Exception) {
            throw Exception("Pool opening failed: ${e.message}")
        }

        GlobalScope.launch {
            pool?.refresh()
            val status = pool?.getStatus()
            logger.debug("Pool status after refresh: $status")
        }
    }

    override suspend fun registerSchema(did: DidInfo, schemaTemplate: SchemaTemplate): String {
        TODO("Not yet implemented")
    }

    override suspend fun getSchema(schemaId: String): Pair<String, Int> {
        TODO("Not yet implemented")
    }

    override suspend fun registerCredentialDefinition(
        did: DidInfo,
        credentialDefinitionTemplate: CredentialDefinitionTemplate,
    ): String {
        TODO("Not yet implemented")
    }

    override suspend fun getCredentialDefinition(id: String): String {
        TODO("Not yet implemented")
    }

    override suspend fun registerRevocationRegistryDefinition(
        did: DidInfo,
        revRegDefTemplate: RevocationRegistryDefinitionTemplate,
    ): String {
        TODO("Not yet implemented")
    }

    override suspend fun getRevocationRegistryDefinition(id: String): String {
        TODO("Not yet implemented")
    }

    override suspend fun getRevocationRegistryDelta(
        id: String,
        to: Int,
        from: Int,
    ): Pair<String, Int> {
        TODO("Not yet implemented")
    }

    override suspend fun getRevocationRegistry(id: String, timestamp: Int): Pair<String, Int> {
        TODO("Not yet implemented")
    }

    override suspend fun revokeCredential(did: DidInfo, credDefId: String, revocationIndex: Int) {
        TODO("Not yet implemented")
    }

    fun close() {
        logger.warn("Do not call close on LedgerBesuService. It will be auto closed")
    }
}

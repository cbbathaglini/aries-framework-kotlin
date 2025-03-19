package org.hyperledger.ariesframework.ledger.ledgerBesu

import ILedgerService
import android.content.Context
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
import org.json.JSONObject
import org.slf4j.LoggerFactory
import uniffi.indy_besu_vdr.ContractSpec
import uniffi.indy_besu_vdr.ContractConfig
import uniffi.indy_besu_vdr.LedgerClient
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.nio.file.Paths


class LedgerBesuService(val agent: Agent, context: Context) : ILedgerService {
    private val logger = LoggerFactory.getLogger(LedgerBesuService::class.java)
    private val appContext: Context = context.applicationContext
    private var pool: Pool? = null
    private val context: Context = context
    private var ledgerBesu: LedgerClient? = null
    private val issuer = Issuer()
    private val jsonIgnoreUnknown = Json { ignoreUnknownKeys = true }

    data class ContractConfigBesu(
        val address: String,
        val specPath: String,
        var spec: ContractSpec? = null
    ) {
        companion object {
            // Modificar o método para aceitar um Context
            fun loadFromFile(context: Context, address: String, specPath: String): uniffi.indy_besu_vdr.ContractConfig {
                // Usando o contexto para acessar o arquivo dentro da pasta assets
                val inputStream = context.assets.open(specPath.trimStart('/'))  // Remove a barra inicial
                val content = inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(content)
                val name = jsonObject.getString("sourceName")
                val abi = jsonObject.getJSONArray("abi").toString()

                return uniffi.indy_besu_vdr.ContractConfig(
                    address = address,
                    specPath = null,
                    spec = ContractSpec(name, abi)
                )
            }
        }
    }

    // Criando configurações individuais para cada contrato
    val didRegistryConfig: ContractConfig by lazy {
            ContractConfigBesu.loadFromFile(
                context = appContext,
                address = "0x0000000000000000000000000000000000018888",
                specPath = "/abi/EthereumExtDidRegistry.json"
            )
    }

    val schemaRegistryConfig: ContractConfig by lazy {
            ContractConfigBesu.loadFromFile(
                context = appContext,
                address = "0x0000000000000000000000000000000000005555",
                specPath = "/abi/SchemaRegistry.json"
            )
    }


    val credentialDefinitionRegistryConfig: ContractConfig by lazy {
            ContractConfigBesu.loadFromFile(
                context = appContext,
                address = "0x0000000000000000000000000000000000004444",
                specPath = "/abi/CredentialDefinitionRegistry.json"
            )
    }

    val revocationRegistryConfig: ContractConfig by lazy {
            ContractConfigBesu.loadFromFile(
                context = appContext,
                address = "0x0000000000000000000000000000000000002222",
                specPath = "/abi/RevocationRegistry.json"
            )
    }

        override suspend fun initialize() {
        logger.info("Initializing Pool")
        if (pool != null) {
            logger.warn("Pool already initialized.")
            return
        }
        var contratos: List<ContractConfig> = listOf(
            didRegistryConfig,
            schemaRegistryConfig,
            credentialDefinitionRegistryConfig,
            revocationRegistryConfig)
        ledgerBesu = LedgerClient(agent.agentConfig.besuLedgerConfig?.chainId?: 0u, agent.agentConfig.besuLedgerConfig?.nodeAddress?: "", contratos, agent.agentConfig.besuLedgerConfig?.network, null)
        setProtocolVersion(2)
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

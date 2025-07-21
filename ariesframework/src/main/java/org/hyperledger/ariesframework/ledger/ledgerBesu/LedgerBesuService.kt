package org.hyperledger.ariesframework.ledger.ledgerBesu

import ILedgerService
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import anoncreds_uniffi.Issuer
import indy_vdr_uniffi.Pool
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.RevocationRegistryDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.proofs.models.RevocationRegistryDelta
import org.hyperledger.ariesframework.wallet.DidInfo
import org.json.JSONObject
import org.slf4j.LoggerFactory
import uniffi.indy_besu_vdr.ContractConfig
import uniffi.indy_besu_vdr.ContractSpec
import uniffi.indy_besu_vdr.LedgerClient
import uniffi.indy_besu_vdr.resolveCredentialDefinition
import uniffi.indy_besu_vdr.resolveRevocationRegistryDefinition
import uniffi.indy_besu_vdr.resolveRevocationRegistryStatusList
import uniffi.indy_besu_vdr.resolveSchema
import uniffi.indy_besu_vdr.revocationStatusListFromString

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
        var spec: ContractSpec? = null,
    ) {
        companion object {
            // Modificar o método para aceitar um Context
            fun loadFromFile(context: Context, address: String, specPath: String): uniffi.indy_besu_vdr.ContractConfig {
                // Usando o contexto para acessar o arquivo dentro da pasta assets
                val inputStream = context.assets.open(specPath.trimStart('/')) // Remove a barra inicial
                val content = inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(content)
                val name = jsonObject.getString("sourceName").substringAfterLast("/").substringBeforeLast(".")
                val abi = jsonObject.getJSONArray("abi").toString()

                return uniffi.indy_besu_vdr.ContractConfig(
                    address = address,
                    specPath = null,
                    spec = ContractSpec(name, abi),
                )
            }
        }
    }

    // Criando configurações individuais para cada contrato
    val didRegistryConfig: ContractConfig by lazy {
        ContractConfigBesu.loadFromFile(
            context = appContext,
            address = "0x0000000000000000000000000000000000018888",
            specPath = "/abi/EthereumExtDidRegistry.json",
        )
    }

    val schemaRegistryConfig: ContractConfig by lazy {
        ContractConfigBesu.loadFromFile(
            context = appContext,
            address = "0x0000000000000000000000000000000000005555",
            specPath = "/abi/SchemaRegistry.json",
        )
    }

    val credentialDefinitionRegistryConfig: ContractConfig by lazy {
        ContractConfigBesu.loadFromFile(
            context = appContext,
            address = "0x0000000000000000000000000000000000004444",
            specPath = "/abi/CredentialDefinitionRegistry.json",
        )
    }

    val revocationRegistryConfig: ContractConfig by lazy {
        ContractConfigBesu.loadFromFile(
            context = appContext,
            address = "0x0000000000000000000000000000000000002222",
            specPath = "/abi/RevocationRegistry.json",
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
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
            revocationRegistryConfig,
        )
        ledgerBesu = LedgerClient(agent.agentConfig.besuLedgerConfig?.chainId ?: 0u, agent.agentConfig.besuLedgerConfig?.nodeAddress ?: "", contratos, agent.agentConfig.besuLedgerConfig?.network, null)
    }

    override suspend fun registerSchema(did: DidInfo, schemaTemplate: SchemaTemplate): String {
        throw Exception("registerSchema not implemented for Besu")
    }

    override suspend fun getSchema(schemaId: String): Pair<String, Int> {
        if (this.ledgerBesu == null) {
            throw Exception("Ledger não foi inicializado")
        }
        var schema = resolveSchema(this.ledgerBesu!!, schemaId)
        val seqNo = 0
        val attrNames = schema.attrNames
        val issuer = schema.issuerId
        val schemaMap = mapOf(
            "name" to JsonPrimitive(schema.name),
            "version" to JsonPrimitive(schema.version),
            "issuerId" to JsonPrimitive(issuer),
            "attrNames" to JsonArray(attrNames.map { JsonPrimitive(it) }),
        )
        val schemaJson = Json.encodeToString(schemaMap)
        return Pair(schemaJson, seqNo)
    }

    override suspend fun registerCredentialDefinition(
        did: DidInfo,
        credentialDefinitionTemplate: CredentialDefinitionTemplate,
    ): String {
        throw Exception("registerCredentialDefinition not implemented for Besu")
    }

    override suspend fun getCredentialDefinition(credentialId: String): String {
        if (this.ledgerBesu == null) {
            throw Exception("Ledger não foi inicializado")
        }
        val credentialDefinition = resolveCredentialDefinition(this.ledgerBesu!!, credentialId)
        logger.info("credentialDefinition >>> ${credentialDefinition.toString()}")

        val json = Json { ignoreUnknownKeys = true }
        val innerJson = json.parseToJsonElement(credentialDefinition.value)
        logger.info("innerJson >>> ${innerJson.toString()}")
        val credDef = mapOf(
            "issuerId" to JsonPrimitive(credentialDefinition.issuerId),
            "schemaId" to JsonPrimitive(credentialDefinition.schemaId),
            "type" to JsonPrimitive(credentialDefinition.credDefType),
            "tag" to JsonPrimitive(credentialDefinition.tag),
            "value" to innerJson,
        )
        logger.info("credDef >>> ${credDef.toString()}")
        var encode : String = "oi"
        try{
            encode = Json.encodeToString(credDef)
        }catch (e: Throwable){
            logger.error("encodeToString >>> ${e.message}")
        }

        logger.info("encode >>> ${encode}")
        return encode
    }

    override suspend fun registerRevocationRegistryDefinition(
        did: DidInfo,
        revRegDefTemplate: RevocationRegistryDefinitionTemplate,
    ): String {
        throw Exception("registerRevocationRegistryDefinition not implemented for Besu")
    }

    override suspend fun getRevocationRegistryDefinition(id: String): String {
        val revocationRD = resolveRevocationRegistryDefinition(this.ledgerBesu!!, id)
        val jsonObject = mapOf(
            "issuerId" to JsonPrimitive(revocationRD.issuerId),
            "revocDefType" to JsonPrimitive(revocationRD.revocDefType),
            "credDefId" to JsonPrimitive(revocationRD.credDefId),
            "tag" to JsonPrimitive(revocationRD.tag),
            "value" to Json.parseToJsonElement(revocationRD.value), // Agora tratado corretamente
        )
        return Json.encodeToString(JsonObject(jsonObject))
    }

    override suspend fun getRevocationRegistryDelta(
        id: String,
        to: Int,
        from: Int,
    ): Pair<String, Int> {
        // RevocationRegistryDelta, from is ignored.
        val revocationStatusList = revocationStatusListFromString(resolveRevocationRegistryStatusList(this.ledgerBesu!!, id, to.toULong()))
        // val revocationDelta = fetchRevocationDelta(this.ledgerBesu!!, id,  to.toULong())
        val revocationRegistryDelta = RevocationRegistryDelta(
            // prevAccum = revocationStatusList.currentAccumulator,
            accum = revocationStatusList.currentAccumulator,
            // issued = revocationDelta!!.issued.map { it.toInt() },
            revoked = revocationStatusList.revocationList.map { it.toInt() },
        )
        val deltaTimestamp = revocationStatusList.timestamp
        return Pair(revocationRegistryDelta.toJsonString(), deltaTimestamp.toInt())
    }

    override suspend fun getRevocationRegistry(id: String, timestamp: Int): Pair<String, Int> {
        val revocationStatusList = revocationStatusListFromString(resolveRevocationRegistryStatusList(this.ledgerBesu!!, id, timestamp.toULong())) // val revocationDelta = fetchRevocationDelta(this.ledgerBesu!!, id,  to.toULong())
        val revocationRegistryDelta = RevocationRegistryDelta(
            // prevAccum = revocationStatusList.currentAccumulator,
            accum = revocationStatusList.currentAccumulator,
            // issued = revocationDelta!!.issued.map { it.toInt() },
            revoked = revocationStatusList.revocationList.map { it.toInt() },
        )
        val deltaTimestamp = revocationStatusList.timestamp
        return Pair(revocationRegistryDelta.toJsonString(), deltaTimestamp.toInt())
    }

    override suspend fun revokeCredential(did: DidInfo, credDefId: String, revocationIndex: Int) {
        throw Exception("revokeCredential not implemented for Besu")
    }

    fun close() {
        logger.warn("Do not call close on LedgerBesuService. It will be auto closed")
    }
}

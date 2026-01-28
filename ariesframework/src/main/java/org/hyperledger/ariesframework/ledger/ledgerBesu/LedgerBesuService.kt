package org.hyperledger.ariesframework.ledger.ledgerBesu

import ILedgerService
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import anoncreds_uniffi.Issuer
import indy_vdr_uniffi.Pool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.cache.AsyncTtlCache
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
import uniffi.indy_besu_vdr.LedgerConfiguration
import uniffi.indy_besu_vdr.LedgerRouter
import uniffi.indy_besu_vdr.RevocationRegistryDefinition
import uniffi.indy_besu_vdr.resolveCredentialDefinition
import uniffi.indy_besu_vdr.resolveRevocationRegistryDefinition
import uniffi.indy_besu_vdr.resolveRevocationRegistryStatusList
import uniffi.indy_besu_vdr.resolveRevocationRegistryStatusListFull
import uniffi.indy_besu_vdr.resolveSchema
import uniffi.indy_besu_vdr.revocationStatusListFromString
import java.io.File

/**
 * LedgerBesuService supports single or multi-ledger configuration dynamically.
 * Contract addresses and specs are loaded from a JSON configuration file.
 */
class LedgerBesuService(val agent: Agent, context: Context) : ILedgerService {
    private val logger = LoggerFactory.getLogger(LedgerBesuService::class.java)
    private val appContext: Context = context.applicationContext
    private var pool: Pool? = null
    private var ledgerClient: LedgerClient? = null
    private var ledgerRouter: LedgerRouter? = null
    private val issuer = Issuer()
    private val jsonIgnoreUnknown = Json { ignoreUnknownKeys = true }

    // TTLs (ajuste ao seu gosto)
    private val rawSchemaCache =
        AsyncTtlCache<String, uniffi.indy_besu_vdr.Schema>(ttlMillis = 10 * 60 * 1000L)
    private val credDefCache = AsyncTtlCache<String, String>(ttlMillis = 10 * 60 * 1000L)
    private val credDefVdrCache =
        AsyncTtlCache<String, uniffi.indy_besu_vdr.CredentialDefinition>(ttlMillis = 10 * 60 * 1000L)
    private val configFilePath: String =
        agent.agentConfig.besuLedgerConfig?.configFile ?: "besu_config.json"
    private val isMultiLedger: Boolean =
        agent.agentConfig.besuLedgerConfig?.multiledger ?: false



    /**
     * Load JSON configuration for all networks and contracts.
     */
    private fun loadLedgerConfig(): JsonObject {
        logger.info("Loading ledger configuration from $configFilePath")
        val inputStream = appContext.assets.open(configFilePath.trimStart('/'))
        val content = inputStream.bufferedReader().use { it.readText() }
        return Json.parseToJsonElement(content).jsonObject
    }

    /**
     * Build a list of ContractConfig objects from a given network section in JSON.
     */
    private fun loadContractConfigsForNetwork(networkJson: JsonObject): List<ContractConfig> {
        val contracts = mutableListOf<ContractConfig>()
        for ((_, value) in networkJson) {
            val obj = value.jsonObject
            val address = obj["address"]?.jsonPrimitive?.content ?: continue
            val specPath = obj["specPath"]?.jsonPrimitive?.content ?: continue

            try {
                val inputStream = appContext.assets.open(specPath.trimStart('/'))
                val abiContent = inputStream.bufferedReader().use { it.readText() }
                val abiJson = JSONObject(abiContent)
                val contractName = abiJson.getString("sourceName")
                    .substringAfterLast("/")
                    .substringBeforeLast(".")
                val abi = abiJson.getJSONArray("abi").toString()

                val spec = ContractSpec(contractName, abi)
                contracts.add(ContractConfig(address = address, specPath = null, spec = spec))
            } catch (e: Exception) {
                logger.error("Failed to load contract spec from $specPath: ${e.message}")
            }
        }
        return contracts
    }

    /**
     * Initialize Ledger client or router depending on configuration.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun initialize() {
        logger.info("Initializing Besu Ledger Service...")

        if (pool != null) {
            logger.warn("Pool already initialized.")
            return
        }

        val configJson = loadLedgerConfig()
        val networksArray = configJson["networks"]?.jsonArray
            ?: throw IllegalArgumentException("Missing 'networks' array in configuration")

        val clients = mutableListOf<LedgerConfiguration>()

        // Se não for multiledger, considera apenas a primeira rede
        val targetNetworks = if (isMultiLedger) {
            logger.info("Multiledger mode enabled: loading all ${networksArray.size} networks")
            networksArray
        } else {
            logger.info("Single-ledger mode enabled: using only the first network entry")
            listOf(networksArray.first())
        }

        for (networkElement in targetNetworks) {
            if (networkElement !is JsonObject) {
                logger.warn("Skipping invalid network entry (not an object)")
                continue
            }

            val networkName = networkElement["networkName"]?.jsonPrimitive?.contentOrNull ?: "default"

            val chainIdStr = networkElement["chainId"]?.jsonPrimitive?.contentOrNull
            val chainId = chainIdStr?.toULongOrNull()
            if (chainId == null) {
                logger.warn("Network $networkName has no valid 'chainId', skipping")
                continue
            }

            val nodeAddress = networkElement["nodeAddress"]?.jsonPrimitive?.contentOrNull
            if (nodeAddress == null) {
                logger.warn("Network $networkName has no valid 'nodeAddress', skipping")
                continue
            }

            val contractsElem = networkElement["contracts"]
            val contractsSection = if (contractsElem is JsonObject) contractsElem else null
            if (contractsSection == null) {
                logger.warn("Network $networkName has no 'contracts' section, skipping")
                continue
            }

            val contractConfigs = loadContractConfigsForNetwork(contractsSection)

            logger.info("Loaded ${contractConfigs.size} contracts for network $networkName")

            val ledgerConfig = LedgerConfiguration(
                chainId = chainId,
                nodeAddress = nodeAddress,
                contractConfigs = contractConfigs,
                network = networkName,
                quorumConfig = null,
            )

            clients.add(ledgerConfig)
        }

        if (clients.isEmpty()) {
            throw IllegalStateException("No valid network configurations found")
        }

        if (isMultiLedger) {
            ledgerRouter = LedgerRouter(clients.toList())
            logger.info("LedgerRouter initialized with ${clients.size} networks.")
        } else {
            val firstConfig = clients.first()
            ledgerClient = LedgerClient(
                chainId = firstConfig.chainId,
                nodeAddress = firstConfig.nodeAddress,
                contractConfigs = firstConfig.contractConfigs,
                network = firstConfig.network,
                quorumConfig = null,
            )
            logger.info("LedgerClient initialized in single-ledger mode (network=${firstConfig.network}).")
        }
    }

    /**
     * Get LedgerClient depending on mode.
     */
    fun getLedgerClient(network: String? = null): LedgerClient {
        return if (isMultiLedger) {
            if (network == null) throw IllegalArgumentException("Network name required for multiledger mode")
            ledgerRouter?.getLedgerForIdentifier(network)
                ?: throw Exception("Ledger not found for network $network")
        } else {
            ledgerClient ?: throw Exception("Ledger client not initialized")
        }
    }

    override suspend fun registerSchema(did: DidInfo, schemaTemplate: SchemaTemplate): String {
        throw Exception("registerSchema not implemented for Besu")
    }

    override suspend fun getSchema(schemaId: String): Pair<String, Int> {
        val start = System.nanoTime()
        logger.info("[CALL] getSchema(schemaId=$schemaId)")

        val schema = getRawSchema(schemaId)
        val seqNo = 0

        val schemaMap = mapOf(
            "name" to JsonPrimitive(schema.name),
            "version" to JsonPrimitive(schema.version),
            "issuerId" to JsonPrimitive(schema.issuerId),
            "attrNames" to JsonArray(schema.attrNames.map { JsonPrimitive(it) }),
        )

        val result = Pair(Json.encodeToString(schemaMap), seqNo)

        val ms = (System.nanoTime() - start) / 1_000_000
        logger.info("[RETURN] getSchema(schemaId=$schemaId) took ${ms}ms")

        return result
    }

    override suspend fun getSchemaObj(schemaId: String): AnonCredsSchema {
        val start = System.nanoTime()
        logger.info("[CALL] getSchemaObj(schemaId=$schemaId)")

        val schema = getRawSchema(schemaId)

        val result = AnonCredsSchema(
            issuerId = schema.issuerId,
            name = schema.name,
            version = schema.version,
            attrNames = schema.attrNames,
        )

        val ms = (System.nanoTime() - start) / 1_000_000
        logger.info("[RETURN] getSchemaObj(schemaId=$schemaId) took ${ms}ms")

        return result
    }

    override suspend fun getSchemas(
        schemaIds: Set<String>,
    ): Map<String, AnonCredsSchema> {
        val schemas = mutableMapOf<String, AnonCredsSchema>()

        for (schemaId in schemaIds) {
            val (schema) = getSchema(schemaId)
            val anonCredsSchema: AnonCredsSchema = Json.decodeFromString(schema)
            schemas[schemaId] = anonCredsSchema
        }

        return schemas
    }

    override suspend fun registerCredentialDefinition(
        did: DidInfo,
        credentialDefinitionTemplate: CredentialDefinitionTemplate,
    ): String {
        throw Exception("registerCredentialDefinition not implemented for Besu")
    }

    override suspend fun getCredentialDefinitionvVdr(
        credentialId: String
    ): uniffi.indy_besu_vdr.CredentialDefinition {
        val start = System.nanoTime()

        val cached = credDefVdrCache.getIfFresh(credentialId)
        if (cached != null) {
            val ms = (System.nanoTime() - start) / 1_000_000
            logger.info("[CACHE HIT] getCredentialDefinitionvVdr($credentialId) took ${ms}ms")
            return cached
        }

        logger.info("[CACHE MISS] getCredentialDefinitionvVdr($credentialId) → fetching from ledger")

        return credDefVdrCache.getOrLoad(credentialId) {
            logger.info("[LEDGER CALL] resolveCredentialDefinition(vdr)($credentialId)")

            val client = ledgerClient ?: getLedgerClient(credentialId)
            ?: throw Exception("Ledger not initialized")

            try {
                val result = resolveCredentialDefinition(client, credentialId)

                val ms = (System.nanoTime() - start) / 1_000_000
                logger.info("[CACHE STORE] getCredentialDefinitionvVdr($credentialId) stored in cache (${ms}ms)")

                result
            } catch (e: Throwable) {
                logger.error("error cred def >>> ${e.message}")
                throw Exception("credential definition not found")
            }
        }
    }

    override suspend fun getCredentialDefinition(credentialId: String): String {
        val start = System.nanoTime()

        val cached = credDefCache.getIfFresh(credentialId)
        if (cached != null) {
            val ms = (System.nanoTime() - start) / 1_000_000
            logger.info("[CACHE HIT] getCredentialDefinition($credentialId) took ${ms}ms")
            return cached
        }

        logger.info("[CACHE MISS] getCredentialDefinition($credentialId) → fetching from ledger")

        return credDefCache.getOrLoad(credentialId) {
            logger.info("[LEDGER CALL] resolveCredentialDefinition($credentialId)")

            val client = ledgerClient ?: getLedgerClient(credentialId)
            ?: throw Exception("Ledger not initialized")

            val credentialDefinition = try {
                resolveCredentialDefinition(client, credentialId)
            } catch (e: Throwable) {
                logger.error("error cred def >>> ${e.message}")
                throw Exception("credential definition not found")
            }

            val innerJson = jsonIgnoreUnknown.parseToJsonElement(credentialDefinition.value)

            val credDef = mapOf(
                "issuerId" to JsonPrimitive(credentialDefinition.issuerId),
                "schemaId" to JsonPrimitive(credentialDefinition.schemaId),
                "type" to JsonPrimitive(credentialDefinition.credDefType),
                "tag" to JsonPrimitive(credentialDefinition.tag),
                "value" to innerJson,
            )

            val result = Json.encodeToString(credDef)

            val ms = (System.nanoTime() - start) / 1_000_000
            logger.info("[CACHE STORE] getCredentialDefinition($credentialId) stored in cache (${ms}ms)")

            result
        }
    }

    override suspend fun registerRevocationRegistryDefinition(
        did: DidInfo,
        revRegDefTemplate: RevocationRegistryDefinitionTemplate,
    ): String {
        throw Exception("registerRevocationRegistryDefinition not implemented for Besu")
    }

    override suspend fun getRevocationRegistryDefinition(id: String): String {
        logger.info("[Besu] Get RevocationRegistryDefinition with id: $id")
        val client = ledgerClient ?: getLedgerClient(id)
            ?: throw Exception("Ledger not initialized")
        val revocationRD = resolveRevocationRegistryDefinition(client, id)
        logger.info("revocationrd: $revocationRD")
        val jsonObject = mapOf(
            "issuerId" to JsonPrimitive(revocationRD.issuerId),
            "revocDefType" to JsonPrimitive(revocationRD.revocDefType),
            "credDefId" to JsonPrimitive(revocationRD.credDefId),
            "tag" to JsonPrimitive(revocationRD.tag),
            "value" to Json.parseToJsonElement(revocationRD.value), // Agora tratado corretamente
        )

        logger.info("revocationrd: $revocationRD")
        return Json.encodeToString(JsonObject(jsonObject))
    }

    override suspend fun getRevocationRegistryDefinitionIndyBesuLib(id: String): RevocationRegistryDefinition {
        logger.info("[Besu] Get RevocationRegistryDefinition with id: $id")
        val client = ledgerClient ?: getLedgerClient(id)
            ?: throw Exception("Ledger not initialized")
        // val revocationRD = resolveRevocationRegistryDefinition(this.ledgerBesu!!, id)
//        logger.info("revocarionrd: ${revocationRD.toString()}")
//        val jsonObject = mapOf(
//            "issuerId" to JsonPrimitive(revocationRD.issuerId),
//            "revocDefType" to JsonPrimitive(revocationRD.revocDefType),
//            "credDefId" to JsonPrimitive(revocationRD.credDefId),
//            "tag" to JsonPrimitive(revocationRD.tag),
//            "value" to Json.parseToJsonElement(revocationRD.value), // Agora tratado corretamente
//        )

        return resolveRevocationRegistryDefinition(client, id)
    }

    override suspend fun getRevocationRegistryDelta(
        id: String,
        to: Int,
        from: Int,
    ): Pair<String, Int> {
        val client = ledgerClient ?: getLedgerClient(id)
            ?: throw Exception("Ledger not initialized")

        // val correct = "did:ethr:0x16bfab61:0x6487221A2b1dc46c1CF1cE6B5420E91a28453AD7/anoncreds/v0/REV_REG_DEF/did:ethr:0x16bfab61:0x6487221A2b1dc46c1CF1cE6B5420E91a28453AD7:identidade_v2:1.0:default/CL_ACCUM/0"

        val def = resolveRevocationRegistryDefinition(
            client = client,
            revRegDefId = id,
        )
        logger.info("def: $def")
        ensureRevRegId(id)
        // val toSeconds = toSecondsULong(to.toLong())

        val revocationStatusList =
            resolveRevocationRegistryStatusListFull(
                client,
                id,
                to.toULong(),
            )

        logger.info("rev status list: $revocationStatusList")
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

    fun toSecondsULong(ts: Long): ULong =
        if (ts > 10_000_000_000L) (ts / 1000L).toULong() else ts.toULong()

    fun ensureRevRegId(id: String) {
        require(
            id.contains("/REV_REG_DEF/") || id.contains("/REV_REG/"),
        ) { "Esperado REV_REG_DEF ou REV_REG id, recebido: $id" }
    }

    override suspend fun getRevocationRegistry(id: String, timestamp: Int): Pair<String, Int> {
        val client = ledgerClient ?: getLedgerClient(id)
            ?: throw Exception("Ledger not initialized")
        val revocationStatusList = revocationStatusListFromString(
            resolveRevocationRegistryStatusList(
                client,
                id,
                timestamp.toULong(),
            ),
        ) // val revocationDelta = fetchRevocationDelta(this.ledgerBesu!!, id,  to.toULong())
        val revocationRegistryDelta = RevocationRegistryDelta(
            // prevAccum = revocationStatusList.currentAccumulator,
            accum = revocationStatusList.currentAccumulator,
            // issued = revocationDelta!!.issued.map { it.toInt() },
            revoked = revocationStatusList.revocationList.map { it.toInt() },
        )
        val deltaTimestamp = revocationStatusList.timestamp
        return Pair(revocationRegistryDelta.toJsonString(), deltaTimestamp.toInt())
    }

    override suspend fun getRevocationStatusList(
        id: String,
        timestamp: ULong,
    ): uniffi.indy_besu_vdr.RevocationStatusList {
        val client = ledgerClient ?: getLedgerClient(id)
            ?: throw Exception("Ledger not initialized")
        val revocationStatusList: uniffi.indy_besu_vdr.RevocationStatusList =
            resolveRevocationRegistryStatusListFull(
                client,
                id,
                timestamp,

            ) // val revocationDelta = fetchRevocationDelta(this.ledgerBesu!!, id,  to.toULong())

        return revocationStatusList
    }

    override suspend fun getTailsPath(): String {
        val tailsFolder = File(agent.context.filesDir.absolutePath, "tails")
        if (!tailsFolder.exists()) {
            tailsFolder.mkdir()
        }
        return tailsFolder.path
    }

    override suspend fun revokeCredential(did: DidInfo, credDefId: String, revocationIndex: Int) {
        throw Exception("revokeCredential not implemented for Besu")
    }

    fun close() {
        logger.warn("Do not call close on LedgerBesuService. It will be auto closed")
    }

    private suspend fun getRawSchema(schemaId: String): uniffi.indy_besu_vdr.Schema {
        val start = System.nanoTime()

        val cached = rawSchemaCache.getIfFresh(schemaId)
        if (cached != null) {
            val ms = (System.nanoTime() - start) / 1_000_000
            logger.info("[CACHE HIT][RAW] schemaId=$schemaId took ${ms}ms")
            return cached
        }

        logger.info("[CACHE MISS][RAW] schemaId=$schemaId → fetching from ledger")

        return rawSchemaCache.getOrLoad(schemaId) {
            logger.info("[LEDGER CALL][RAW] resolveSchema($schemaId)")

            val client = ledgerClient ?: getLedgerClient(schemaId)
            ?: throw Exception("Ledger not initialized")

            val schema = resolveSchema(client, schemaId)

            val ms = (System.nanoTime() - start) / 1_000_000
            logger.info("[CACHE STORE][RAW] schemaId=$schemaId stored (${ms}ms)")

            schema
        }
    }

    // cache functions
    fun clearLedgerCaches() {
        rawSchemaCache.clear()
        credDefCache.clear()
    }

    fun invalidateSchema(schemaId: String) = rawSchemaCache.invalidate(schemaId)
    fun invalidateCredDef(credDefId: String) = credDefCache.invalidate(credDefId)
}

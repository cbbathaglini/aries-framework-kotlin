package org.hyperledger.ariesframework.ledger.ledgerBesu

import ILedgerService
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import anoncreds_uniffi.Issuer
import indy_besu_vdr.ContractConfig
import indy_besu_vdr.ContractSpec
import indy_besu_vdr.LedgerClient
import indy_besu_vdr.LedgerConfiguration
import indy_besu_vdr.LedgerRouter
import indy_besu_vdr.RevocationRegistryDefinition
import indy_besu_vdr.resolveCredentialDefinition
import indy_besu_vdr.resolveRevocationRegistryDefinition
import indy_besu_vdr.resolveRevocationRegistryStatusList
import indy_besu_vdr.resolveRevocationRegistryStatusListFull
import indy_besu_vdr.resolveSchema
import indy_besu_vdr.revocationStatusListFromString
import indy_vdr_uniffi.Pool
import kotlinx.serialization.builtins.serializer
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
import org.hyperledger.ariesframework.cache.DiskOnlyAsyncTtlCache
import org.hyperledger.ariesframework.cache.LedgerCacheConfig // ✅ NEW
import org.hyperledger.ariesframework.cache.LedgerCacheDefaults
import org.hyperledger.ariesframework.cache.RevRegDefDto
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.RevocationRegistryDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.proofs.models.RevocationRegistryDelta
import org.hyperledger.ariesframework.util.LogUtil
import org.hyperledger.ariesframework.wallet.DidInfo
import org.json.JSONObject
import java.io.File

/**
 * LedgerBesuService supports single or multi-ledger configuration dynamically.
 * Contract addresses and specs are loaded from a JSON configuration file.
 */
class LedgerBesuService(val agent: Agent, context: Context) : ILedgerService {
    private val appContext: Context = context.applicationContext
    private var pool: Pool? = null
    private var ledgerClient: LedgerClient? = null
    private var ledgerRouter: LedgerRouter? = null
    private val issuer = Issuer()
    private val jsonIgnoreUnknown = Json { ignoreUnknownKeys = true }

    private val cacheConfigFile: String =
        agent.agentConfig.cacheConfigFile ?: "config.properties"

    private val DAY_MS = 24L * 60 * 60 * 1000

    private val cacheCfg: LedgerCacheConfig by lazy {
        LedgerCacheConfig.load(appContext, assetFileName = cacheConfigFile)
    }

    private fun credDefTtlDaysFor(credDefId: String): Long =
        cacheCfg.credDefTtlDaysById[credDefId]
            ?: cacheCfg.credDefDefaultDays

    private val credDefJsonCachesByDays =
        mutableMapOf<Long, DiskOnlyAsyncTtlCache<String, String>>()

    private fun credDefJsonCacheFor(credDefId: String): DiskOnlyAsyncTtlCache<String, String> {
        val days = credDefTtlDaysFor(credDefId).coerceAtLeast(1L)
        return credDefJsonCachesByDays.getOrPut(days) {
            DiskOnlyAsyncTtlCache(
                context = appContext,
                cacheName = "${LedgerCacheDefaults.CRED_DEF_JSON}_$days",
                ttlMillis = days * DAY_MS,
                keyToString = { it },
                valueSerializer = String.serializer(),
            )
        }
    }

    private val credDefVdrCachesByDays =
        mutableMapOf<Long, DiskOnlyAsyncTtlCache<String, String>>()

    private fun credDefVdrCacheFor(credDefId: String): DiskOnlyAsyncTtlCache<String, String> {
        val days = credDefTtlDaysFor(credDefId).coerceAtLeast(1L)
        return credDefVdrCachesByDays.getOrPut(days) {
            DiskOnlyAsyncTtlCache(
                context = appContext,
                cacheName = "${LedgerCacheDefaults.CRED_DEF_VDR_JSON}_$days",
                ttlMillis = days * DAY_MS,
                keyToString = { it },
                valueSerializer = String.serializer(),
            )
        }
    }

    @kotlinx.serialization.Serializable
    private data class CredDefVdrCacheDto(
        val issuerId: String,
        val schemaId: String,
        val credDefType: String,
        val tag: String,
        val value: String,
    )

    private fun toDto(v: indy_besu_vdr.CredentialDefinition): CredDefVdrCacheDto =
        CredDefVdrCacheDto(
            issuerId = v.issuerId,
            schemaId = v.schemaId,
            credDefType = v.credDefType,
            tag = v.tag,
            value = v.value,
        )

    private fun fromDto(dto: CredDefVdrCacheDto): indy_besu_vdr.CredentialDefinition =
        indy_besu_vdr.CredentialDefinition(
            issuerId = dto.issuerId,
            schemaId = dto.schemaId,
            credDefType = dto.credDefType,
            tag = dto.tag,
            value = dto.value,
        )

    private val schemaJsonCache = DiskOnlyAsyncTtlCache<String, String>(
        context = appContext,
        cacheName = LedgerCacheDefaults.SCHEMA_JSON,
        ttlMillis = cacheCfg.schemaTtlDays * DAY_MS,
        keyToString = { it },
        valueSerializer = String.serializer(),
    )

    private val revRegDefCache: DiskOnlyAsyncTtlCache<String, RevRegDefDto> =
        DiskOnlyAsyncTtlCache(
            context = appContext,
            cacheName = LedgerCacheDefaults.REG_DEF,
            ttlMillis = cacheCfg.revRegTtlDays * DAY_MS,
            keyToString = { it },
            valueSerializer = RevRegDefDto.serializer(),
        )

    private val tailsPathCache = DiskOnlyAsyncTtlCache<String, String>(
        context = appContext,
        cacheName = "tailsPath",
        ttlMillis = cacheCfg.tailsTtlDays * DAY_MS,
        keyToString = { it },
        valueSerializer = String.serializer(),
    )

    private val configFilePath: String =
        agent.agentConfig.besuLedgerConfig?.configFile ?: "besu_config.json"
    private val isMultiLedger: Boolean =
        agent.agentConfig.besuLedgerConfig?.multiledger ?: false

    /**
     * Load JSON configuration for all networks and contracts.
     */
    private fun loadLedgerConfig(): JsonObject {
        LogUtil.info(this) { "Loading ledger configuration from $configFilePath" }
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
                LogUtil.error(this, e) { "Failed to load contract spec from $specPath: ${e.message}" }
            }
        }
        return contracts
    }

    /**
     * Initialize Ledger client or router depending on configuration.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun initialize() {
        BesuVdr.configureNativeLibrary()
        LogUtil.info(this) { "Initializing Besu Ledger Service..." }

        if (pool != null) {
            LogUtil.warn(this) { "Pool already initialized" }
            return
        }

        val configJson = loadLedgerConfig()
        val networksArray = configJson["networks"]?.jsonArray
            ?: throw IllegalArgumentException("Missing 'networks' array in configuration")

        val clients = mutableListOf<LedgerConfiguration>()

        val targetNetworks = if (isMultiLedger) {
            LogUtil.info(this) { "Multiledger mode enabled: loading all ${networksArray.size} networks" }
            networksArray
        } else {
            LogUtil.info(this) { "Single-ledger mode enabled: using only the first network entry" }
            listOf(networksArray.first())
        }

        for (networkElement in targetNetworks) {
            if (networkElement !is JsonObject) {
                LogUtil.warn(this) { "Skipping invalid network entry (not an object)" }
                continue
            }

            val networkName = networkElement["networkName"]?.jsonPrimitive?.contentOrNull ?: "default"

            val chainIdStr = networkElement["chainId"]?.jsonPrimitive?.contentOrNull
            val chainId = chainIdStr?.toULongOrNull()
            if (chainId == null) {
                LogUtil.warn(this) { "Network $networkName has no valid 'chainId', skipping" }
                continue
            }

            val nodeAddress = networkElement["nodeAddress"]?.jsonPrimitive?.contentOrNull
            if (nodeAddress == null) {
                LogUtil.warn(this) { "Network $networkName has no valid 'nodeAddress', skipping" }
                continue
            }

            val contractsElem = networkElement["contracts"]
            val contractsSection = if (contractsElem is JsonObject) contractsElem else null
            if (contractsSection == null) {
                LogUtil.warn(this) { "Network $networkName has no 'contracts' section, skipping" }
                continue
            }

            val contractConfigs = loadContractConfigsForNetwork(contractsSection)

            LogUtil.info(this) { "Loaded ${contractConfigs.size} contracts for network $networkName" }

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
            LogUtil.info(this) { "LedgerRouter initialized with ${clients.size} networks." }
        } else {
            val firstConfig = clients.first()
            ledgerClient = LedgerClient(
                chainId = firstConfig.chainId,
                nodeAddress = firstConfig.nodeAddress,
                contractConfigs = firstConfig.contractConfigs,
                network = firstConfig.network,
                quorumConfig = null,
            )
            LogUtil.info(this) { "LedgerClient initialized in single-ledger mode (network=${firstConfig.network})." }
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
        val schemaJson = getRawSchemaJson(schemaId)
        val seqNo = 0

        val ms = (System.nanoTime() - start) / 1_000_000
        LogUtil.info(this) { "schema= $schemaId took ${ms}ms" }

        return Pair(schemaJson, seqNo)
    }

    override suspend fun getSchemaObj(schemaId: String): AnonCredsSchema {
        val start = System.nanoTime()

        val schemaJson = getRawSchemaJson(schemaId)
        val obj = jsonIgnoreUnknown.parseToJsonElement(schemaJson).jsonObject

        val issuerId = obj["issuerId"]!!.jsonPrimitive.content
        val name = obj["name"]!!.jsonPrimitive.content
        val version = obj["version"]!!.jsonPrimitive.content
        val attrNames = obj["attrNames"]!!.jsonArray.map { it.jsonPrimitive.content }

        val result = AnonCredsSchema(
            issuerId = issuerId,
            name = name,
            version = version,
            attrNames = attrNames,
        )

        val ms = (System.nanoTime() - start) / 1_000_000
        LogUtil.info(this) { "schema= $schemaId took ${ms}ms" }

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
        credentialId: String,
    ): indy_besu_vdr.CredentialDefinition {
        val start = System.nanoTime()
        val ttlDays = credDefTtlDaysFor(credentialId)
        val cache = credDefVdrCacheFor(credentialId)

        val cached = cache.getIfFresh(credentialId)
        if (cached != null) {
            val dto = jsonIgnoreUnknown.decodeFromString<CredDefVdrCacheDto>(cached)
            val ms = (System.nanoTime() - start) / 1_000_000
            LogUtil.info(this) { "[cache hit] credential definition vdr= $credentialId ttlDays=$ttlDays took ${ms}ms" }
            return fromDto(dto)
        }

        LogUtil.info(this) { "[cache miss] credential definition vdr= $credentialId ttlDays=$ttlDays → fetching from ledger" }

        val json = cache.getOrLoad(credentialId) {
            val client = ledgerClient ?: getLedgerClient(credentialId)
                ?: throw Exception("Ledger not initialized")

            val vdr = try {
                resolveCredentialDefinition(client, credentialId)
            } catch (e: Throwable) {
                LogUtil.error(this, e) { "error cred def vdr >>> ${e.message}" }
                throw Exception("credential definition not found")
            }

            val dtoJson = jsonIgnoreUnknown.encodeToString(toDto(vdr))

            val ms = (System.nanoTime() - start) / 1_000_000
            LogUtil.info(this) { "[cache store] credential definition vdr= $credentialId ttlDays=$ttlDays stored (${ms}ms)" }

            dtoJson
        }

        val dto = jsonIgnoreUnknown.decodeFromString<CredDefVdrCacheDto>(json)
        return fromDto(dto)
    }

    override suspend fun getCredentialDefinition(credentialId: String): String {
        val start = System.nanoTime()

        val ttlDays = credDefTtlDaysFor(credentialId)
        val cache = credDefJsonCacheFor(credentialId)

        val cached = cache.getIfFresh(credentialId)
        if (cached != null) {
            val ms = (System.nanoTime() - start) / 1_000_000
            LogUtil.info(this) { "[cache hit] credential definition = $credentialId ttlDays=$ttlDays took (${ms}ms)" }
            return cached
        }

        LogUtil.info(this) { "[cache miss] credential definition = $credentialId ttlDays=$ttlDays → fetching from ledger" }

        return cache.getOrLoad(credentialId) {
            val client = ledgerClient ?: getLedgerClient(credentialId)

            val credentialDefinition = try {
                resolveCredentialDefinition(client, credentialId)
            } catch (e: Throwable) {
                LogUtil.error(this, e) { "error credential definition = ${e.message}" }
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
            LogUtil.info(this) { "[cache store] credential definition = $credentialId ttlDays=$ttlDays (${ms}ms)" }
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
        LogUtil.info(this) { "get RevocationRegistryDefinition with id: $id" }
        val client = ledgerClient ?: getLedgerClient(id)
            ?: throw Exception("Ledger not initialized")
        val revocationRD = resolveRevocationRegistryDefinition(client, id)

        val jsonObject = mapOf(
            "issuerId" to JsonPrimitive(revocationRD.issuerId),
            "revocDefType" to JsonPrimitive(revocationRD.revocDefType),
            "credDefId" to JsonPrimitive(revocationRD.credDefId),
            "tag" to JsonPrimitive(revocationRD.tag),
            "value" to Json.parseToJsonElement(revocationRD.value),
        )

        LogUtil.info(this) { "get RevocationRegistryDefinition: $revocationRD" }
        return Json.encodeToString(JsonObject(jsonObject))
    }

    override suspend fun getRevocationRegistryDefinitionIndyBesuLib(
        id: String,
    ): RevocationRegistryDefinition {
        val start = System.nanoTime()
        LogUtil.info(this) { "get getRevocationRegistryDefinitionIndyBesuLib with id: $id" }

        val cached = revRegDefCache.getIfFresh(id)
        if (cached != null) {
            val ms = (System.nanoTime() - start) / 1_000_000
            LogUtil.info(this) { "[cache hit] revRegDef($id) took ${ms}ms" }

            return RevocationRegistryDefinition(
                issuerId = cached.issuerId,
                revocDefType = cached.revocDefType,
                credDefId = cached.credDefId,
                tag = cached.tag,
                value = cached.value,
            )
        }

        LogUtil.info(this) { "[cache miss] revRegDef($id) → fetching from ledger" }

        val dto: RevRegDefDto = revRegDefCache.getOrLoad(id) {
            LogUtil.info(this) { "resolveRevocationRegistryDefinition id = $id" }

            val client = ledgerClient ?: getLedgerClient(id)
                ?: throw Exception("Ledger not initialized")

            val rr = resolveRevocationRegistryDefinition(client, id)

            val created = RevRegDefDto(
                issuerId = rr.issuerId,
                revocDefType = rr.revocDefType,
                credDefId = rr.credDefId,
                tag = rr.tag,
                value = rr.value,
            )

            val ms = (System.nanoTime() - start) / 1_000_000
            LogUtil.info(this) { "[cache store] revRegDef($id) stored (${ms}ms)" }

            created
        }

        val ms = (System.nanoTime() - start) / 1_000_000

        return RevocationRegistryDefinition(
            issuerId = dto.issuerId,
            revocDefType = dto.revocDefType,
            credDefId = dto.credDefId,
            tag = dto.tag,
            value = dto.value,
        )
    }

    override suspend fun getRevocationRegistryDelta(
        id: String,
        to: Int,
        from: Int,
    ): Pair<String, Int> {
        val client = ledgerClient ?: getLedgerClient(id)
            ?: throw Exception("Ledger not initialized")

        val def = resolveRevocationRegistryDefinition(
            client = client,
            revRegDefId = id,
        )
        ensureRevRegId(id)

        val revocationStatusList =
            resolveRevocationRegistryStatusListFull(
                client,
                id,
                to.toULong(),
            )

        val revocationRegistryDelta = RevocationRegistryDelta(
            accum = revocationStatusList.currentAccumulator,
            revoked = revocationStatusList.revocationList.map { it.toInt() },
        )
        val deltaTimestamp = revocationStatusList.timestamp
        return Pair(revocationRegistryDelta.toJsonString(), deltaTimestamp.toInt())
    }

    fun ensureRevRegId(id: String) {
        require(
            id.contains("/REV_REG_DEF/") || id.contains("/REV_REG/"),
        ) { "Expected REV_REG_DEF or REV_REG id, received: $id" }
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
        )
        val revocationRegistryDelta = RevocationRegistryDelta(
            accum = revocationStatusList.currentAccumulator,
            revoked = revocationStatusList.revocationList.map { it.toInt() },
        )
        val deltaTimestamp = revocationStatusList.timestamp
        return Pair(revocationRegistryDelta.toJsonString(), deltaTimestamp.toInt())
    }

    override suspend fun getRevocationStatusList(
        id: String,
        timestamp: ULong,
    ): indy_besu_vdr.RevocationStatusList {
        val client = ledgerClient ?: getLedgerClient(id)
            ?: throw Exception("Ledger not initialized")
        return resolveRevocationRegistryStatusListFull(
            client,
            id,
            timestamp,
        )
    }

    override suspend fun getTailsPath(): String {
        val start = System.nanoTime()

        val cached = tailsPathCache.getIfFresh(LedgerCacheDefaults.TAILS_PATH)
        LogUtil.info(this) { "[getTailsPath] cached path $cached" }
        if (cached != null) {
            val ms = (System.nanoTime() - start) / 1_000_000
            LogUtil.info(this) { "[cache hit] getTailsPath took ${ms}ms" }
            return cached
        }

        LogUtil.info(this) { "[cache miss] getTailsPath → creating folder if needed" }

        val path = tailsPathCache.getOrLoad(LedgerCacheDefaults.TAILS_PATH) {
            val tailsFolder = File(agent.context.filesDir, "tails")
            if (!tailsFolder.exists()) {
                tailsFolder.mkdir()
                LogUtil.info(this) { "tails directory created at ${tailsFolder.absolutePath}" }
            } else {
                LogUtil.info(this) { "tails directory already exists at ${tailsFolder.absolutePath}" }
            }
            tailsFolder.absolutePath
        }

        val ms = (System.nanoTime() - start) / 1_000_000
        LogUtil.info(this) { "[cache store] getTailsPath stored (${ms}ms)" }

        return path
    }

    override suspend fun revokeCredential(did: DidInfo, credDefId: String, revocationIndex: Int) {
        throw Exception("revokeCredential not implemented for Besu")
    }

    fun close() {
        LogUtil.warn(this) { "Do not call close on LedgerBesuService. It will be auto closed" }
    }

    private suspend fun getRawSchemaJson(schemaId: String): String {
        val start = System.nanoTime()

        val cached = schemaJsonCache.getIfFresh(schemaId)
        if (cached != null) {
            val ms = (System.nanoTime() - start) / 1_000_000
            LogUtil.info(this) { "[cache hit] schemaId=$schemaId took ${ms}ms" }
            return cached
        }

        LogUtil.info(this) { "[cache miss] schemaId=$schemaId → fetching from ledger" }

        return schemaJsonCache.getOrLoad(schemaId) {
            val client = ledgerClient ?: getLedgerClient(schemaId)
            val schema = resolveSchema(client, schemaId)

            val schemaMap = mapOf(
                "name" to JsonPrimitive(schema.name),
                "version" to JsonPrimitive(schema.version),
                "issuerId" to JsonPrimitive(schema.issuerId),
                "attrNames" to JsonArray(schema.attrNames.map { JsonPrimitive(it) }),
            )

            val json = Json.encodeToString(schemaMap)

            val ms = (System.nanoTime() - start) / 1_000_000
            LogUtil.info(this) { "[cache store] schemaId=$schemaId stored (${ms}ms)" }

            json
        }
    }

    // cache functions
    fun clearLedgerCaches() {
        schemaJsonCache.clear()

        credDefJsonCachesByDays.values.forEach { it.clear() }
        credDefJsonCachesByDays.clear()

        credDefVdrCachesByDays.values.forEach { it.clear() }
        credDefVdrCachesByDays.clear()

        revRegDefCache.clear()
        tailsPathCache.clear()
    }

    fun invalidateCredDef(credDefId: String) =
        credDefJsonCacheFor(credDefId).invalidate(credDefId)

    fun invalidateCredDefVdr(credDefId: String) =
        credDefVdrCacheFor(credDefId).invalidate(credDefId)
}

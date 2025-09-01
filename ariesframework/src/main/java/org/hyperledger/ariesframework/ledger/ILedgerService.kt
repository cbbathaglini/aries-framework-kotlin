
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.RevocationRegistryDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.wallet.DidInfo
import uniffi.indy_besu_vdr.RevocationRegistryDefinition
import uniffi.indy_besu_vdr.RevocationStatusList

interface ILedgerService {
    suspend fun initialize()
    suspend fun registerSchema(did: DidInfo, schemaTemplate: SchemaTemplate): String
    suspend fun getSchema(schemaId: String): Pair<String, Int>
    suspend fun getSchemas(schemaIds: Set<String>): Map<String, AnonCredsSchema>
    suspend fun registerCredentialDefinition(
        did: DidInfo,
        credentialDefinitionTemplate: CredentialDefinitionTemplate,
    ): String
    suspend fun getCredentialDefinition(id: String): String
    suspend fun registerRevocationRegistryDefinition(
        did: DidInfo,
        revRegDefTemplate: RevocationRegistryDefinitionTemplate,
    ): String
    suspend fun getRevocationRegistryDefinition(id: String): String
    suspend fun getRevocationRegistryDefinitionIndyBesuLib(id: String): RevocationRegistryDefinition
    suspend fun getRevocationRegistryDelta(
        id: String,
        to: Int = (System.currentTimeMillis() / 1000L).toInt(),
        from: Int = 0,
    ): Pair<String, Int>
    suspend fun getRevocationRegistry(id: String, timestamp: Int): Pair<String, Int>
    suspend fun revokeCredential(did: DidInfo, credDefId: String, revocationIndex: Int)

    suspend fun getRevocationStatusList(id: String, timestamp: Int): RevocationStatusList
    suspend fun getTailsPath(): String
    suspend fun getSchemaObj(schemaId: String): AnonCredsSchema

}

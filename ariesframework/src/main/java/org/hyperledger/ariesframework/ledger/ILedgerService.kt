import android.content.Context
import org.hyperledger.ariesframework.ledger.CredentialDefinitionTemplate
import org.hyperledger.ariesframework.ledger.RevocationRegistryDefinitionTemplate
import org.hyperledger.ariesframework.ledger.SchemaTemplate
import org.hyperledger.ariesframework.wallet.DidInfo

interface ILedgerService {
    suspend fun initialize()
    suspend fun registerSchema(did: DidInfo, schemaTemplate: SchemaTemplate): String
    suspend fun getSchema(schemaId: String): Pair<String, Int>
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
    suspend fun getRevocationRegistryDelta(
        id: String,
        to: Int = (System.currentTimeMillis() / 1000L).toInt(),
        from: Int = 0,
    ): Pair<String, Int>
    suspend fun getRevocationRegistry(id: String, timestamp: Int): Pair<String, Int>
    suspend fun revokeCredential(did: DidInfo, credDefId: String, revocationIndex: Int)
}

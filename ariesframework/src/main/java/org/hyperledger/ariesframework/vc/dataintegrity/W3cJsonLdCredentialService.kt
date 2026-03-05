package org.hyperledger.ariesframework.vc.dataintegrity

import android.content.Context
import com.google.gson.JsonElement
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.vc.modules.W3cCredentialsModuleConfig
import org.hyperledger.ariesframework.vc.util.W3cTypeExpander

class W3cJsonLdCredentialService(
    private val agent: Agent,
    private val w3cCredentialsModuleConfig: W3cCredentialsModuleConfig,
    private val context: Context,

) {
    private val appContext: Context = context.applicationContext

    suspend fun getExpandedTypesForCredential(
        contextList: List<kotlinx.serialization.json.JsonElement>,
        types: List<String>,
    ): Map<String, List<String>> {
        val expanded = W3cTypeExpander.expandTypes(
            W3cTypeExpander.ContextSpec(contexts = contextList),
            types,
        )

        return mapOf("type" to expanded)
    }
}

package org.hyperledger.ariesframework.vc.dataintegrity

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.util.PrintLongLine
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.modules.W3cCredentialsModuleConfig
import org.hyperledger.ariesframework.vc.service.W3cCredentialService
import org.hyperledger.ariesframework.vc.util.W3cTypeExpander
import org.slf4j.LoggerFactory

class W3cJsonLdCredentialService(
    private val agent: Agent,
    private val w3cCredentialsModuleConfig: W3cCredentialsModuleConfig,
    private val context: Context

) {
    private val logger = LoggerFactory.getLogger(W3cJsonLdCredentialService::class.java)
    private val appContext: Context = context.applicationContext

    suspend fun getExpandedTypesForCredential(
        credential: W3cJsonLdVerifiableCredential
    ): Map<String, List<String>> {

        logger.info("credential2 => $credential")

        val contextList = credential.context
        val types = credential.type

        val expanded = W3cTypeExpander.expandTypes(
            W3cTypeExpander.ContextSpec(contexts = contextList),
            types
        )

//        val localContexts = mapOf(
//            "https://www.w3.org/2018/credentials/v1" to W3cCredentialsModuleConfig.loadFile(context= appContext, file= "/types/credentials_2018.json"),
//            "https://w3id.org/security/data-integrity/v2" to W3cCredentialsModuleConfig.loadFile(context= appContext, file= "/types/security-data-integrity-v2.json")
//        )
//        PrintLongLine.print("localcontexts: ${localContexts}")
        return mapOf("type" to expanded)
    }
}
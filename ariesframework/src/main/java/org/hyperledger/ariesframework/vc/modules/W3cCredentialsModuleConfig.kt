package org.hyperledger.ariesframework.vc.modules

import android.content.Context
import org.hyperledger.ariesframework.util.PrintLongLine
import org.json.JSONObject

class W3cCredentialsModuleConfig(
    private val options: W3cCredentialsModuleConfigOptions = W3cCredentialsModuleConfigOptions(),
) {
    val documentLoader: DocumentLoader
        get() = options.documentLoader ?: defaultDocumentLoader

    companion object {
        fun loadFile(context: Context, file: String): JSONObject {
            val inputStream = context.assets.open(file.trimStart('/')) // Remove the leading slash
            val content = inputStream.bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(content)

            PrintLongLine.print("document: " + jsonObject.toString())
            return jsonObject
        }
    }
}

data class W3cCredentialsModuleConfigOptions(
    val documentLoader: DocumentLoader? = null,
)

typealias DocumentLoader = (url: String) -> Any

val defaultDocumentLoader: DocumentLoader = { url ->
    println("defaultDocumentLoader to $url")
}

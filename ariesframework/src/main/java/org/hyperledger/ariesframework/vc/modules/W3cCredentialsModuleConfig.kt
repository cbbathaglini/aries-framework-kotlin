package org.hyperledger.ariesframework.vc.modules

class W3cCredentialsModuleConfig(
    private val options: W3cCredentialsModuleConfigOptions = W3cCredentialsModuleConfigOptions()
) {
    val documentLoader: DocumentLoader
        get() = options.documentLoader ?: defaultDocumentLoader
}

data class W3cCredentialsModuleConfigOptions(
    val documentLoader: DocumentLoader? = null
)

typealias DocumentLoader = (url: String) -> Any

val defaultDocumentLoader: DocumentLoader = { url ->
    println("defaultDocumentLoader to $url")
}
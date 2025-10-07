package org.hyperledger.ariesframework.vc.util

/**
 * Expands the "type" values of a W3C VC using minimal rules:
 * - Known terms from VC v1 (e.g., "VerifiableCredential").
 * - Prefixes defined in the context (e.g., { "ex": "https://example.com/" } -> "ex:Foo").
 * - @vocab as a fallback for simple terms (without ":" and without a direct mapping).
 * - Absolute IRIs are preserved.
 *
 * This is NOT a full JSON-LD processor; it only handles what's necessary for "type".
 */
object W3cTypeExpander {

    data class ContextSpec(
        val contexts: List<Any?>, // Strings (URLs) e/ou Map<String, Any>
        val additionalTermMap: Map<String, String> = emptyMap(),
    )

    private val ABSOLUTE_IRI_REGEX =
        Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")

    // comum terms in https://www.w3.org/2018/credentials/v1
    private val VC_V1_TERMS: Map<String, String> = mapOf(
        "VerifiableCredential" to "https://www.w3.org/2018/credentials#VerifiableCredential",
        "VerifiablePresentation" to "https://www.w3.org/2018/credentials#VerifiablePresentation",
        "CredentialStatusList2021" to "https://www.w3.org/2018/credentials#CredentialStatusList2021",
        "CredentialSubject" to "https://www.w3.org/2018/credentials#CredentialSubject",
        "issuer" to "https://www.w3.org/2018/credentials#issuer",
        "issuanceDate" to "https://www.w3.org/2018/credentials#issuanceDate",
        "expirationDate" to "https://www.w3.org/2018/credentials#expirationDate",
        // add more
    )

    /**
     * Expande uma lista de tipos, dado um conjunto de contextos (strings e mapas).
     *
     * @param spec ContextSpec contendo @context (strings e/ou mapas JSON-LD inline).
     * @param types lista de valores de "type" (por exemplo ["VerifiableCredential", "EmployeeIDCredential"])
     */
    fun expandTypes(spec: ContextSpec, types: List<String>): List<String> {
        val (termMap, prefixMap, vocab) = buildResolutionMaps(spec.contexts, spec.additionalTermMap)
        return types.mapNotNull { raw ->
            val t = raw.trim()
            when {
                t.isEmpty() -> null
                ABSOLUTE_IRI_REGEX.matches(t) -> t // já é IRI absoluto
                ":" in t -> expandCurie(t, prefixMap) ?: fallbackTerm(t, termMap, vocab)
                else -> fallbackTerm(t, termMap, vocab)
            }
        }.distinct()
    }

    private data class ResolutionMaps(
        val termMap: Map<String, String>,
        val prefixMap: Map<String, String>,
        val vocab: String?,
    )

    private fun buildResolutionMaps(
        contexts: List<Any?>,
        additionalTermMap: Map<String, String>,
    ): ResolutionMaps {
        val termMap = mutableMapOf<String, String>()
        val prefixMap = mutableMapOf<String, String>()
        var vocab: String? = null

        // Inclui termos conhecidos do VC v1 por padrão
        termMap.putAll(VC_V1_TERMS)
        termMap.putAll(additionalTermMap)

        contexts.forEach { ctx ->
            when (ctx) {
                is String -> {
                    if (ctx.contains("www.w3.org/2018/credentials/v1")) {
                        // já cobrimos com VC_V1_TERMS
                    }
                    // data-integrity/v2 não define tipos usados em "type" normalmente
                }
                is Map<*, *> -> {
                    ctx.forEach { (k, v) ->
                        val key = k?.toString() ?: return@forEach
                        when (key) {
                            "@vocab" -> vocab = v?.toString()
                            else -> {
                                val value = v?.toString() ?: return@forEach
                                if (ABSOLUTE_IRI_REGEX.matches(value)) {
                                    prefixMap[key] = value.ensureTrailingHashOrSlash()
                                } else {
                                    if (ABSOLUTE_IRI_REGEX.matches(value)) {
                                        termMap[key] = value
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // ignorar tipos inesperados de contexto
                }
            }
        }

        return ResolutionMaps(termMap.toMap(), prefixMap.toMap(), vocab)
    }

    private fun expandCurie(curie: String, prefixMap: Map<String, String>): String? {
        val idx = curie.indexOf(':')
        if (idx <= 0 || idx == curie.lastIndex) return null
        val prefix = curie.substring(0, idx)
        val local = curie.substring(idx + 1)
        val base = prefixMap[prefix] ?: return null
        return base + local
    }

    private fun fallbackTerm(term: String, termMap: Map<String, String>, vocab: String?): String {
        termMap[term]?.let { return it }
        if (vocab != null) return vocab.ensureTrailingHashOrSlash() + term
        return term
    }

    private fun String.ensureTrailingHashOrSlash(): String =
        if (endsWith("#") || endsWith("/")) this else "$this#"
}

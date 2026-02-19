package org.hyperledger.ariesframework.vc.dataintegrity

typealias Constants = Any

interface Suites {
    val linkedDataSignature: Any
    val linkedDataProof: Any
}

interface Purposes {
    val assertionProofPurpose: Any
}

object JsonLdSuites : Suites {
    override val linkedDataSignature: Any = Any() // substituir com a classe real
    override val linkedDataProof: Any = Any() // substituir com a classe real
}

object JsonLdPurposes : Purposes {
    override val assertionProofPurpose: Any = Any() // substituir com a classe real
}

object JsonLdConstants

val suites: Suites = JsonLdSuites
val purposes: Purposes = JsonLdPurposes
val constants: Constants = JsonLdConstants

package org.hyperledger.ariesframework.proofs.formats

interface ProofFormat {
    val formatKey: String // e.g. "presentationExchange"
    val proofFormats: ProofFormats
    val formatData: FormatData
}

interface ProofFormats {
    val createProposal: Any?
    val acceptProposal: Any?
    val createRequest: Any?
    val acceptRequest: Any?

    val getCredentialsForRequest: RequestIO
    val selectCredentialsForRequest: RequestIO
}

interface RequestIO {
    val input: Any?
    val output: Any?
}

interface FormatData {
    val proposal: Any?
    val request: Any?
    val presentation: Any?
}
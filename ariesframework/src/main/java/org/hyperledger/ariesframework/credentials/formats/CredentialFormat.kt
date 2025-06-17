package org.hyperledger.ariesframework.credentials.formats

interface CredentialFormat {
    val formatKey: String                  // ex: "w3c"
    val credentialRecordType: String       // ex: "w3c"
    val credentialFormats: CredentialFormatOperations
    val formatData: FormatData
}

data class CredentialFormatOperations(
    val createProposal: Any?,
    val acceptProposal: Any?,
    val createOffer: Any?,
    val acceptOffer: Any?,
    val createRequest: Any?,
    val acceptRequest: Any?
)

data class FormatData(
    val proposal: Any?,
    val offer: Any?,
    val request: Any?,
    val credential: Any?
)
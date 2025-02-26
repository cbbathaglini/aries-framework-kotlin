package org.hyperledger.ariesframework.credentialsv2.formats

/**
 * Represents the payload for a specific method from a list of CredentialFormat interfaces.
 *
 * Example:
 * ```
 * typealias CreateOfferCredentialFormats = CredentialFormatPayload<List<IndyCredentialFormat, JsonLdCredentialFormat>, "createOffer">
 * ```
 * This would be equivalent to:
 * ```
 * typealias CreateOfferCredentialFormats = mapOf(
 *     "indy" to { ... params for indy create offer ... },
 *     "jsonld" to { ... params for jsonld create offer ... }
 * )
 * ```
 */
typealias CredentialFormatPayload<CFs, M> = Map<String, Any?>

/**
 * Represents a credential format with various format-specific properties.
 */
interface CredentialFormat {
    val formatKey: String // e.g., "credentialManifest", cannot be shared between different formats
    val credentialRecordType: String // e.g., "w3c", can be shared between multiple formats

    val credentialFormats: CredentialFormats
    val formatData: FormatData

    /**
     * Represents the available credential format methods.
     */
    data class CredentialFormats(
        val createProposal: Any?,
        val acceptProposal: Any?,
        val createOffer: Any?,
        val acceptOffer: Any?,
        val createRequest: Any?,
        val acceptRequest: Any?
    )

    /**
     * Represents different types of credential format data.
     */
    data class FormatData(
        val proposal: Any?,
        val offer: Any?,
        val request: Any?,
        val credential: Any?
    )
}
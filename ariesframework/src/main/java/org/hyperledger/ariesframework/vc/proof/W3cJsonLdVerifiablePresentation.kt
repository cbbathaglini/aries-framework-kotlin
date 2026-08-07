package org.hyperledger.ariesframework.vc.proof

import kotlinx.serialization.Serializable

@Serializable
class W3cJsonLdVerifiablePresentation
//
// ) : W3cPresentation {
//
//    var proof: List<Proof>
//
//    init {
//        proof = if (options.proof.cryptosuite != null) {
//            listOf(DataIntegrityProof(options.proof))
//        } else {
//            listOf(LinkedDataProof(options.proof as LinkedDataProofOptions))
//        }
//    }
//
//    val proofTypes: List<String>
//        get() = proof.map { it.type }
//
//    val dataIntegrityCryptosuites: List<String>
//        get() = proof
//            .filterIsInstance<DataIntegrityProof>()
//            .filter { it.type == "DataIntegrityProof" && it.cryptosuite != null }
//            .map { it.cryptosuite!! }
//
//    fun toJson(): String {
//        return JsonTransformer.toJSON(this)
//    }
//
//    /**
//     * The ClaimFormat of the presentation. For JSON-LD credentials, always `ldp_vp`.
//     */
//    val claimFormat: ClaimFormat
//        get() = ClaimFormat.LdpVp
//
//    /**
//     * Returns the encoded version of the W3C Verifiable Presentation.
//     * For JSON-LD presentations, this is a JSON object.
//     */
//    val encoded: String
//        get() = toJson()
// }

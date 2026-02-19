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
//     * O ClaimFormat da apresentação. Para JSON-LD credentials, sempre `ldp_vp`.
//     */
//    val claimFormat: ClaimFormat
//        get() = ClaimFormat.LdpVp
//
//    /**
//     * Retorna a versão codificada da W3C Verifiable Presentation.
//     * Para JSON-LD presentations, isto é um objeto JSON.
//     */
//    val encoded: String
//        get() = toJson()
// }

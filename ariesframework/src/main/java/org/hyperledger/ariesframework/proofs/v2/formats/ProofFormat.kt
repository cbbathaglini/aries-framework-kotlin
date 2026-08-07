package org.hyperledger.ariesframework.proofs.v2.formats

import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.FormatDataProofAnonCreds
import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.ProofFormatAnoncreds

interface ProofFormat {
    val formatKey: String // e.g. "presentationExchange"
    val proofFormats: ProofFormatAnoncreds
    val formatData: FormatDataProofAnonCreds
}

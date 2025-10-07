package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import org.hyperledger.ariesframework.proofs.formats.ProofFormat

data class AnoncredsProofFormat(
    override val formatKey: String = "anoncreds",
    override val proofFormats: ProofFormatAnoncreds,
    override val formatData: FormatDataProofAnonCreds,
) : ProofFormat

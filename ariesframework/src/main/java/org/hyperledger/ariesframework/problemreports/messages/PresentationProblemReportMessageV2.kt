package org.hyperledger.ariesframework.problemreports.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
class PresentationProblemReportMessageV2 private constructor() : BaseProblemReportMessage(
    DescriptionOptions(
        "Proof abandoned",
        "abandoned",
    ),
    null,
) {
    var proofRecord: ProofExchangeRecord? = null

    constructor(threadId: String) : this() {
        thread = ThreadDecorator(threadId)
        type = Companion.type
    }

    constructor(
        threadId: String,
        descriptionOptions: DescriptionOptions,
        proofRecord: ProofExchangeRecord,
    ) : this() {
        thread = ThreadDecorator(threadId)
        type = Companion.type
        description = descriptionOptions
        this.proofRecord = proofRecord
    }

    companion object {
        const val type = "https://didcomm.org/present-proof/2.0/problem-report"
    }
}

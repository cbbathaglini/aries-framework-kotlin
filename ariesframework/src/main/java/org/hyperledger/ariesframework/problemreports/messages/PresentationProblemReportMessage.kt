package org.hyperledger.ariesframework.problemreports.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

@Serializable
class PresentationProblemReportMessage private constructor() : BaseProblemReportMessage(
    // See: https://github.com/hyperledger/aries-cloudagent-python/blob/main/aries_cloudagent/protocols/present_proof/v1_0/messages/presentation_problem_report.py // ktlint-disable max-line-length
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
        proofRecord: ProofExchangeRecord,
    ) : this() {
        thread = ThreadDecorator(threadId)
        type = PresentationProblemReportMessage.type
        this.proofRecord = proofRecord
    }

    companion object {
        const val type = "https://didcomm.org/present-proof/1.0/problem-report"
    }
}

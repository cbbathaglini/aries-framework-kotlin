package org.hyperledger.ariesframework.problemreports.messages

import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator

@Serializable
class PresentationProblemReportMessageV2 private constructor() : BaseProblemReportMessage(
    DescriptionOptions(
        "Proof abandoned",
        "abandoned",
    ),
    null,
) {
    constructor(threadId: String) : this() {
        thread = ThreadDecorator(threadId)
        type = Companion.type
    }
    companion object {
        const val type = "https://didcomm.org/present-proof/2.0/problem-report"
    }
}

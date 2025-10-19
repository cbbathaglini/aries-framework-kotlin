package org.hyperledger.ariesframework.proofs.messages.v2

import org.hyperledger.ariesframework.anoncreds.exception.ProblemReportError
import org.hyperledger.ariesframework.problemreports.messages.DescriptionOptions
import org.hyperledger.ariesframework.problemreports.messages.PresentationProblemReportMessageV2
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord

class PresentationProblemReportErrorV2(
    message: String,
    problemCode: String,
    threadId: String,
    proofRecord: ProofExchangeRecord,
) : ProblemReportError(message, problemCode) {

    val problemReportError: PresentationProblemReportMessageV2 =
        PresentationProblemReportMessageV2(
            threadId = threadId,
            descriptionOptions = DescriptionOptions(
                en = message,
                code = problemCode,
            ),
            proofRecord = proofRecord,
        )
}

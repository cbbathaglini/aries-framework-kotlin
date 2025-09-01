package org.hyperledger.ariesframework.anoncreds.exception

import org.hyperledger.ariesframework.error.CredoError

class ProblemReportError(
    message: String,
    problemCode: String,
) : CredoError(message) {

    val problemReport: ProblemReportMessage = ProblemReportMessage(
        description = ProblemReportDescription(
            en = message,
            code = problemCode,
        ),
    )
}

data class ProblemReportMessage(
    val description: ProblemReportDescription,
)

data class ProblemReportDescription(
    val en: String,
    val code: String,
)

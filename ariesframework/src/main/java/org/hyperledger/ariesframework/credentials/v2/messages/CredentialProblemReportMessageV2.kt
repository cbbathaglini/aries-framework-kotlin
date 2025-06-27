package org.hyperledger.ariesframework.credentials.v2.messages

import kotlinx.serialization.SerialName
import org.hyperledger.ariesframework.credentials.models.problemreport.FixHintOptions
import org.hyperledger.ariesframework.credentials.models.problemreport.ProblemReportMessage
import org.hyperledger.ariesframework.credentials.v2.models.problemreport.ImpactStatus
import org.hyperledger.ariesframework.credentials.v2.models.problemreport.WhereStatus
import org.hyperledger.ariesframework.credentials.v2.models.problemreport.WhoRetriesStatus
import org.hyperledger.ariesframework.problemreports.messages.DescriptionOptions

@SerialName(CredentialProblemReportMessageV2.TYPE)
class CredentialProblemReportMessageV2(
    override val description: DescriptionOptions,

    @SerialName("problem_items")
    override val problemItems: List<String>? = null,

    @SerialName("who_retries")
    override val whoRetries: WhoRetriesStatus? = null,

    @SerialName("fix_hint")
    override val fixHint: FixHintOptions? = null,

    override val impact: ImpactStatus? = null,
    override val where: WhereStatus? = null,

    @SerialName("noticed_time")
    override val noticedTime: String? = null,

    @SerialName("tracking_uri")
    override val trackingUri: String? = null,

    @SerialName("escalation_uri")
    override val escalationUri: String? = null,
) : ProblemReportMessage(
    description = description,
    problemItems = problemItems,
    whoRetries = whoRetries,
    fixHint = fixHint,
    impact = impact,
    where = where,
    noticedTime = noticedTime,
    trackingUri = trackingUri,
    escalationUri = escalationUri,
) {
    companion object {
        const val TYPE = "https://didcomm.org/issue-credential/2.0/problem-report"
    }
}
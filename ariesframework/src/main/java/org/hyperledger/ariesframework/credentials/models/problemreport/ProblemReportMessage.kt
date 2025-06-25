package org.hyperledger.ariesframework.credentials.models.problemreport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.credentials.modelv2.ImpactStatus
import org.hyperledger.ariesframework.credentials.modelv2.WhereStatus
import org.hyperledger.ariesframework.credentials.modelv2.WhoRetriesStatus
import org.hyperledger.ariesframework.problemreports.messages.DescriptionOptions

@Serializable
open class ProblemReportMessage(

    open val description: DescriptionOptions,

    @SerialName("problem_items")
    open val problemItems: List<String>? = null,

    @SerialName("who_retries")
    open val whoRetries: WhoRetriesStatus? = null,

    @SerialName("fix_hint")
    open val fixHint: FixHintOptions? = null,

    open val impact: ImpactStatus? = null,
    open val where: WhereStatus? = null,

    @SerialName("noticed_time")
    open val noticedTime: String? = null,

    @SerialName("tracking_uri")
    open val trackingUri: String? = null,

    @SerialName("escalation_uri")
    open val escalationUri: String? = null,


    ) : AgentMessage(generateId(), type) {
    companion object {
        const val type = "https://didcomm.org/notification/2.0/problem-report"

    }
}
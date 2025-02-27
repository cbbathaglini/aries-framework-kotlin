package org.hyperledger.ariesframework.credentials.v2.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.credentials.v2.CredentialsV2Constants
import org.hyperledger.ariesframework.credentials.v2.models.ProblemReportBody

@Serializable
class CredentialProblemReportMessageV2 : AgentMessage(generateId(), type) {

    @SerialName("pthid")
    val pthid: String? = null

    @SerialName("ack")
    val ack: List<String>? = null

    @SerialName("body")
    val body: ProblemReportBody? = null


    companion object {
        const val type = CredentialsV2Constants.PROBLEM_REPORT
    }
}


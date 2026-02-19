package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.OutboundPackage
import org.hyperledger.ariesframework.util.LogUtil

class SubjectOutboundTransport(private val subject: Agent) : OutboundTransport {

    override suspend fun sendPackage(_package: OutboundPackage) {
        LogUtil.info(this) { "Sending outbound message to subject ${subject.agentConfig.label}" }
        subject.receiveMessage(_package.payload)
    }
}

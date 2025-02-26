package org.hyperledger.ariesframework.credentialsv2.models

import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.credentials.repository.CredentialExchangeRecord

data class CredentialProtocolMsgReturnType<MessageType : AgentMessage>(
    val credentialRecord: CredentialExchangeRecord,
    val message: MessageType
)
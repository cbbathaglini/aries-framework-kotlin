package org.hyperledger.ariesframework.agent

import org.hyperledger.ariesframework.basicmessage.messages.BasicMessageInfos
import org.hyperledger.ariesframework.connection.repository.ConnectionRecord
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialExchangeRecord
import org.hyperledger.ariesframework.oob.repository.OutOfBandRecord
import org.hyperledger.ariesframework.problemreports.messages.BaseProblemReportMessage
import org.hyperledger.ariesframework.proofs.repository.ProofExchangeRecord
import org.hyperledger.ariesframework.routing.repository.MediationRecord

sealed interface AgentEvents {
    class ConnectionEvent(val record: ConnectionRecord) : AgentEvents
    class MediationEvent(val record: MediationRecord) : AgentEvents
    class OutOfBandEvent(val record: OutOfBandRecord) : AgentEvents
    class CredentialEvent(val record: CredentialExchangeRecord) : AgentEvents
    class ProofEvent(val record: ProofExchangeRecord) : AgentEvents
    class BasicMessageEvent(val message: BasicMessageInfos) : AgentEvents
    class ProblemReportEvent(val message: BaseProblemReportMessage) : AgentEvents
    class ProblemReportNotificationEvent(val message: BaseProblemReportMessage) : AgentEvents
    class RevocationNotificationReceivedEvent(val record: CredentialExchangeRecord) : AgentEvents
    class RevocationNotificationReceivedEventV2(val record: CredentialExchangeRecord) : AgentEvents
    class CredentialEventV2(val record: CredentialExchangeRecord) : AgentEvents

}

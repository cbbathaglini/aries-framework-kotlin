package org.hyperledger.ariesframework.credentials.messages

import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.v1.repository.CredentialExchangeRecord

interface IOfferCredentialMessage {
    val attachment: Attachment
    val credentialRecord: CredentialExchangeRecord
}
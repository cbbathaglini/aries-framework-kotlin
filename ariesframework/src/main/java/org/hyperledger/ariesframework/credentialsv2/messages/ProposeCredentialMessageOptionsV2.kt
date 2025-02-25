package org.hyperledger.ariesframework.credentialsv2.messages

import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentialsv2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentialsv2.models.Formats

interface ProposeCredentialMessageOptionsV2{
    val id: String?
    val formats: List<Formats>
    val proposalAttachments: List<Attachment>
    val comment: String?
    val goalCode: String?
    val goal: String?
    val credentialPreview: CredentialPreviewV2?
    val attachments: List<Attachment>?
}

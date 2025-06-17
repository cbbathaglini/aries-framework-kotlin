package org.hyperledger.ariesframework.credentials.models

import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentials.v2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentials.v2.models.Format

data class CredentialFormatCreateOfferReturn (
   val attachment: Attachment,
   val format: Format,
   val previewAttributes: List<CredentialPreviewV2>
){

}
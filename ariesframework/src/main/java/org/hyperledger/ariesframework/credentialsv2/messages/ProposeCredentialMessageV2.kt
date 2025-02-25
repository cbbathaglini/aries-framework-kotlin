package org.hyperledger.ariesframework.credentialsv2.messages

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.hyperledger.ariesframework.agent.AgentMessage
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.credentialsv2.models.CredentialPreviewV2
import org.hyperledger.ariesframework.credentialsv2.models.Formats


@Serializable
class ProposeCredentialMessageV2(

    @SerialName("goal_code")
    var goalCode: String? = null,

    var goal: String? = null,

    var comment: String? = null,

    @SerialName("credential_preview")
    var credentialPreview: CredentialPreviewV2?,

    var formats: List<Formats>,

    @SerialName("filters~attach")
    var proposalAttachments: List<Attachment>,

    @SerialName("attachments")
    var attachments: List<Attachment>?,

) : AgentMessage(generateId(), type) {
    companion object {
        const val type = "https://didcomm.org/issue-credential/2.0/propose-credential"
    }

//    fun constructor(options: ProposeCredentialMessageOptionsV2) {
//        //super()
//        if (options!=null) {
//            this.id = if(options.id!=null) options.id else generateId()
//            this.comment = options.comment
//            this.credentialPreview = options.credentialPreview
//            this.formats = options.formats
//            this.proposalAttachments = options.proposalAttachments
//            this.attachments = options.attachments
//        }
//    }

    fun getProposalAttachmentById(id: String): Attachment? {
        return proposalAttachments.find { it.id == id }
    }
}

/**
 *
 * public getProposalAttachmentById(id: string) {
 *         return this.proposalAttachments.find((attachment) => attachment.id === id)
 *     }
 */

/**
 *
 *
 * {
 *
 *     "@id": "<uuid of propose-message>",
 *     "goal_code": "<goal-code>",
 *     "comment": "<some comment>",
 *     "credential_preview": <json-ld object>,
 *     "formats" : [
 *         {
 *             "attach_id" : "<attach@id value>",
 *             "format" : "<format-and-version>"
 *         }
 *     ],
 *     "filters~attach": [
 *         {
 *             "@id": "<attachment identifier>",
 *             "mime-type": "application/json",
 *             "data": {
 *                 "base64": "<bytes for base64>"
 *             }
 *         }
 *     ]
 * }
 */


/**
 *
 * export class V2ProposeCredentialMessage extends AgentMessage {
 *   public constructor(options: V2ProposeCredentialMessageOptions) {
 *     super()
 *     if (options) {
 *       this.id = options.id ?? this.generateId()
 *       this.comment = options.comment
 *       this.credentialPreview = options.credentialPreview
 *       this.formats = options.formats
 *       this.proposalAttachments = options.proposalAttachments
 *       this.appendedAttachments = options.attachments
 *     }
 *   }
 *
 *   @IsValidMessageType(V2ProposeCredentialMessage.type)
 *   public readonly type = V2ProposeCredentialMessage.type.messageTypeUri
 *   public static readonly type = parseMessageType('https://didcomm.org/issue-credential/2.0/propose-credential')
 *
 *   /**
 *    * Human readable information about this Credential Proposal,
 *    * so the proposal can be evaluated by human judgment.
 *    */
 *   @IsOptional()
 *   @IsString()
 *   public comment?: string
 *
 *   public getProposalAttachmentById(id: string): Attachment | undefined {
 *     return this.proposalAttachments.find((attachment) => attachment.id === id)
 *   }
 * }
 *
 *
 */

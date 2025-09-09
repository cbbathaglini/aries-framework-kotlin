package org.hyperledger.ariesframework.anoncreds.formats.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.decorators.Attachment
import org.hyperledger.ariesframework.agent.decorators.AttachmentData
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialOffer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.FetchSchemaReturn
import org.hyperledger.ariesframework.credentials.formats.LinkedAttachment
import org.hyperledger.ariesframework.credentials.models.CredentialPreviewAttribute
import org.hyperledger.ariesframework.credentials.models.CredentialLinkedAttachmentsResult
import org.hyperledger.ariesframework.credentials.utils.Functions
import org.hyperledger.ariesframework.credentials.utils.JsonEncoder
import org.hyperledger.ariesframework.error.CredoError

class FormatDataUtil {
    companion object{
        /**
         * Returns an object of type {@link Attachment} for use in credential exchange messages.
         * It looks up the correct format identifier and encodes the data as a base64 attachment.
         *
         * @param data The data to include in the attach object
         * @param id the attach id from the formats component of the message
         */
        fun getFormatData(data: Any, id: String): Attachment {
            val base64 = JsonEncoder.toBase64(data)

            return Attachment(
                id = id,
                mimetype = "application/json",
                data = AttachmentData(base64 = base64)
            )
        }


        fun getCredentialLinkedAttachments(
            attributes: List<CredentialPreviewAttribute>? = null,
            linkedAttachments: List<LinkedAttachment>? = null
        ): CredentialLinkedAttachmentsResult {
            if (linkedAttachments == null && attributes == null) {
                return CredentialLinkedAttachmentsResult()
            }

            var previewAttributesResult = attributes ?: emptyList()
            var attachments: List<Attachment>? = null

            if (linkedAttachments != null) {
                previewAttributesResult = Functions.createAndLinkAttachmentsToPreview(linkedAttachments, previewAttributesResult)
                attachments = linkedAttachments.map { it.attachment }
            }

            return CredentialLinkedAttachmentsResult(
                attachments = attachments,
                previewAttributes = previewAttributesResult
            )
        }

        inline fun <reified T> parseAttachmentData(attachment: Attachment): T {
            val jsonString = attachment.getDataAsJson()
            val jsonElement = Json.parseToJsonElement(jsonString)
            return Json.decodeFromJsonElement(jsonElement)
        }

        suspend fun fetchSchema(agent: Agent, schemaId: String) : FetchSchemaReturn {

            val result = agent.anonCredsRegistryService
                .getRegistryForIdentifier(schemaId)
                .getSchema(agent, schemaId)

            if (result.schema == null) {
                throw CredoError("Schema not found for id $schemaId: ${result.resolutionMetadata?.message}")
            }

            return FetchSchemaReturn(
                schema = result.schema,
                schemaId = result.schemaId,
                indyNamespace = result.schemaMetadata["didIndyNamespace"] as? String
            )

        }

        suspend fun assertPreviewAttributesMatchSchemaAttributes(agent: Agent, anoncredsCredentialOffer : AnonCredsCredentialOffer, previewAttributes:  List<CredentialPreviewAttribute>){
            val fetchSchemaReturn = fetchSchema(agent, anoncredsCredentialOffer.schemaId)
            assertAttributesMatch(fetchSchemaReturn.schema, previewAttributes)
        }


        fun assertAttributesMatch(schema: AnonCredsSchema, attributes: List<CredentialPreviewAttribute>) {
            val schemaAttributes = schema.attrNames
            val credAttributes = attributes.map { it.name }

            val difference = (credAttributes - schemaAttributes) + (schemaAttributes - credAttributes)

            if (difference.isNotEmpty()) {
                throw CredoError(
                    "The credential preview attributes do not match the schema attributes (difference is: $difference, needs: $schemaAttributes)"
                )
            }
        }
    }
}
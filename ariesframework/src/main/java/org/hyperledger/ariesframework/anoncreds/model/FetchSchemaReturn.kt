package org.hyperledger.ariesframework.anoncreds.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory

@Serializable
data class FetchSchemaReturn(
    val schema: AnonCredsSchema,
    val schemaId: String,
    val indyNamespace: String? = null,
) {

    companion object {
        private val logger = LoggerFactory.getLogger(FetchSchemaReturn::class.java)

        fun fromJson(jsonElementSchema: JsonElement, schemaId: String): FetchSchemaReturn {
            val objectSchema = jsonElementSchema.jsonObject

            logger.info("objectSchema $objectSchema")

            val attrNamesList: List<String> = objectSchema["attrNames"]
                ?.jsonArray // Ensure it's a JsonArray
                ?.map { it.jsonPrimitive.content } // Convert each element to String
                ?: emptyList()

            val anoncredsSchema = AnonCredsSchema(
                issuerId = objectSchema.get("issuerId").toString(),
                name = objectSchema.get("name").toString(),
                version = objectSchema.get("version").toString(),
                attrNames = attrNamesList,
            )

            return FetchSchemaReturn(
                schema = anoncredsSchema,
                schemaId = schemaId,
            )
        }
    }
}

package org.hyperledger.ariesframework.anoncreds.formats.anoncreds

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnonCredsPresentationPreviewAttribute(
    val name: String,
    @SerialName("cred_def_id")
    val credentialDefinitionId: String? = null,
    @SerialName("mimeType")
    val mimeType: String? = null,
    val value: String? = null,
    val referent: String? = null,
)

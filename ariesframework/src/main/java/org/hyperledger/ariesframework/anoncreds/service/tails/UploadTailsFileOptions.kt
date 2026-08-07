package org.hyperledger.ariesframework.anoncreds.service.tails

import kotlinx.serialization.Serializable

@Serializable
data class UploadTailsFileOptions(
    val tailsFilePath: String,
)

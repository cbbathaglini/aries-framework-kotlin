package org.hyperledger.ariesframework.anoncreds.service.tails

import kotlinx.serialization.Serializable

@Serializable
data class GetTailsFileResult (
    val tailsFilePath: String
){
}
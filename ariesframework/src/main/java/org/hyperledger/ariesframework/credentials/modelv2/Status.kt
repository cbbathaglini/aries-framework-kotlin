package org.hyperledger.ariesframework.credentials.modelv2
import kotlinx.serialization.Serializable

@Serializable
enum class WhoRetriesStatus {
    YOU, ME, BOTH, NONE
}

@Serializable
enum class ImpactStatus {
    MESSAGE, THREAD, CONNECTION
}

@Serializable
enum class WhereStatus {
    CLOUD, EDGE, WIRE, AGENCY
}

@Serializable
enum class OtherStatus {
    YOU, ME, OTHER
}
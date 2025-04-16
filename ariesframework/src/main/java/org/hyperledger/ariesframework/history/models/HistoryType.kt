package org.hyperledger.ariesframework.history.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class HistoryType {
    @SerialName("basic-message-received")
    BasicMessageReceived,

    @SerialName("connection-created")
    ConnectionCreated,

    @SerialName("credential-offer-accepted")
    CredentialOfferAccepted,

    @SerialName("credential-offer-declined")
    CredentialOfferDeclined,

    @SerialName("credential-offer-received")
    CredentialOfferReceived,

    @SerialName("credential-revoked")
    CredentialRevoked,

    @SerialName("proof-request-accepted")
    ProofRequestAccepted,

    @SerialName("proof-request-declined")
    ProofRequestDeclined,

    @SerialName("proof-request-received")
    ProofRequestReceived,
}

package org.hyperledger.ariesproject.menu

enum class MainMenu(val text: String) {
    GET("Scan a QR code"),
    LIST("Credentials"),
    HISTORICAL("Connections Historical"),
    REQUESTPROOF("(Verifier) Request proof"),
    SCANREQUESTPROOF("(Holder) Scan request proof"),
    RECEIVING_PRESENTATION_PROOF("(Verifier) Receiving presentation proof"),
    PRESENTATION_LIST("(Holder) Presentation List"),
    CONNECTION("Generate invitation"),
}

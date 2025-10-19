package org.hyperledger.ariesproject.menu

enum class MainMenu(val text: String) {
    GET("Scan a QR code"),
    LIST("Credentials"),
    HISTORICAL("Connections Historical"),
    REQUESTPROOF("(Verifier) Request proof"),
    SCANREQUESTPROOF("(Holder) Scan request proof"),
    CONNECTION("Generate invitation"),
}

package org.hyperledger.ariesframework.credentials.v2.models.problemreport

enum class WhoRetriesStatus(val value: String) {
    You("YOU"),
    Me("ME"),
    Both("BOTH"),
    None("NONE")
}

enum class ImpactStatus(val value: String) {
    Message("MESSAGE"),
    Thread("THREAD"),
    Connection("CONNECTION")
}

enum class WhereStatus(val value: String) {
    Cloud("CLOUD"),
    Edge("EDGE"),
    Wire("WIRE"),
    Agency("AGENCY")
}

enum class OtherStatus(val value: String) {
    You("YOU"),
    Me("ME"),
    Other("OTHER")
}

package org.hyperledger.ariesframework.ledger.ledgerBesu

internal object BesuVdr {
    @Synchronized
    fun configureNativeLibrary() {
        // Version 0.3.1.1 requests indy_besu_vdr but packages indy_besu_vdr_uniffi.
        // Use the binding's supported override before its first native call.
        val property = "uniffi.component.indy_besu_vdr.libraryOverride"
        if (System.getProperty(property) == null) {
            System.setProperty(property, "indy_besu_vdr_uniffi")
        }
    }
}

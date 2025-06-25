package org.hyperledger.ariesframework.credentials.formats

import org.hyperledger.ariesframework.anoncreds.formats.CredentialFormatOperations

interface CredentialFormat {
    val formatKey: String                  // ex: "w3c"
    val credentialRecordType: String       // ex: "w3c"
    val credentialFormats: CredentialFormatOperations
    val formatData: FormatData
}

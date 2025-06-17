package org.hyperledger.ariesframework.credentials

import org.hyperledger.ariesframework.credentials.formats.CredentialFormatService

data class V2CredentialProtocolConfig<T : CredentialFormatService>(
    val credentialFormats: List<T>
)
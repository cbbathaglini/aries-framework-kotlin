package org.hyperledger.ariesframework.anoncreds.exception

import org.hyperledger.ariesframework.error.CredoError

open class AnonCredsError(
    message: String,
    cause: Throwable? = null
) : CredoError(message, cause)
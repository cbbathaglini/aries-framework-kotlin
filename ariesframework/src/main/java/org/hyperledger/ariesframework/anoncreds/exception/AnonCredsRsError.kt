package org.hyperledger.ariesframework.anoncreds.exception

class AnonCredsRsError(
    message: String,
    cause: Throwable? = null,
) : AnonCredsError(message, cause)

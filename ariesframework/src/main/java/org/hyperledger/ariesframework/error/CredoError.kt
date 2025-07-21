package org.hyperledger.ariesframework.error

open class BaseError(message: String, cause: Throwable? = null) : Exception(message, cause)

open class CredoError(
    message: String,
    cause: Throwable? = null,
) : BaseError(message, cause)

package org.hyperledger.ariesframework.credentials.models

import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

/**
 * Configuration options for the credential module.
 *
 * @property autoAcceptCredentials Defines whether credentials should be automatically accepted.
 */
data class CredentialsModuleConfigOptions(
    val autoAcceptCredentials: AutoAcceptCredential? = null,
)

/**
 * Configuration class for the credential module.
 * Accepts a set of options and exposes getters for read access.
 */
class CredentialsModuleConfig(
    private val options: CredentialsModuleConfigOptions,
) {

    /**
     * Defines whether the module should automatically accept credential messages.
     * Default: [AutoAcceptCredential.Never]
     */
    val autoAcceptCredentials: AutoAcceptCredential
        get() = options.autoAcceptCredentials ?: AutoAcceptCredential.Never
}
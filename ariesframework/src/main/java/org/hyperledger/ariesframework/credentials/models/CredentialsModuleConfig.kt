package org.hyperledger.ariesframework.credentials.models

import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

/**
 * Configurações para o módulo de credenciais.
 *
 * @property autoAcceptCredentials Define se as credenciais devem ser aceitas automaticamente.
 * @property credentialProtocols Lista de protocolos de credencial disponíveis.
 */
data class CredentialsModuleConfigOptions(
    val autoAcceptCredentials: AutoAcceptCredential? = null,
)

/**
 * Classe de configuração para o módulo de credenciais.
 * Aceita um conjunto de opções e expõe getters para leitura.
 */
class CredentialsModuleConfig(
    private val options: CredentialsModuleConfigOptions
) {

    /**
     * Define se o módulo deve aceitar automaticamente mensagens de credenciais.
     * Padrão: [AutoAcceptCredential.Never]
     */
    val autoAcceptCredentials: AutoAcceptCredential
        get() = options.autoAcceptCredentials ?: AutoAcceptCredential.Never

}
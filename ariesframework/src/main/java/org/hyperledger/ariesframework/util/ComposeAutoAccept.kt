package org.hyperledger.ariesframework.util

import org.hyperledger.ariesframework.credentials.v1.models.AutoAcceptCredential

/**
 * Returns the credential auto-accept configuration based on the following priority:
 *  - The record-level configuration takes precedence
 *  - If not set, the agent-level configuration is used
 *  - If neither is set, [AutoAcceptCredential.Never] is returned by default
 *
 * @param recordConfig Auto-accept configuration defined at the credential record level (may be null)
 * @param agentConfig Auto-accept configuration defined at the agent level (may be null)
 * @return The resolved [AutoAcceptCredential] value
 */
fun composeAutoAccept(
    recordConfig: AutoAcceptCredential?,
    agentConfig: AutoAcceptCredential?
): AutoAcceptCredential {
    return recordConfig ?: agentConfig ?: AutoAcceptCredential.Never
}
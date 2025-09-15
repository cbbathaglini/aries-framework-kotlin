package org.hyperledger.ariesframework.proofs.models

fun composeAutoAccept(
    recordConfig: AutoAcceptProof?,
    agentConfig: AutoAcceptProof?,
): AutoAcceptProof {
    return recordConfig ?: agentConfig ?: AutoAcceptProof.Never
}

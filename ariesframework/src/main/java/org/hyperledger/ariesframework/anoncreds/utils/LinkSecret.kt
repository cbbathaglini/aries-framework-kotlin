package org.hyperledger.ariesframework.anoncreds.utils

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.exception.AnonCredsRsError

class LinkSecret {
    companion object{
        suspend fun getLinkSecret(agent: Agent, linkSecretId: String): String {
            val linkSecretRecord = agent.anonCredsLinkSecretRepository
                .getByLinkSecretId(linkSecretId)

            if (linkSecretRecord.value == null) {
                throw AnonCredsRsError("Link Secret value not stored")
            }

            return linkSecretRecord.value
        }
    }
}
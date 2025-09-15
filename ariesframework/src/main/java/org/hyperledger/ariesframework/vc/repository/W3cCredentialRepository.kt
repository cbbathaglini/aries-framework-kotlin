package org.hyperledger.ariesframework.vc.repository

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.storage.Repository

class W3cCredentialRepository(agent: Agent) : Repository<W3cCredentialRecord>(
    W3cCredentialRecord::class,
    agent,
)

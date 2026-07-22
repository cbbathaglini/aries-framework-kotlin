package org.hyperledger.ariesframework.webvh

import org.hyperledger.ariesframework.agent.Agent

class WebVhModule(val agent: Agent) {

    val resolver = WebVhDidResolver()
    val anonCredsRegistry = WebVhAnonCredsRegistry()

    suspend fun resolveDid(did: String) = resolver.resolve(did)
}

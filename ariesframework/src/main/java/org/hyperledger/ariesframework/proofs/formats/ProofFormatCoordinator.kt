package org.hyperledger.ariesframework.proofs.formats

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.anoncreds.formats.AnoncredsCredentialFormatService
import org.hyperledger.ariesframework.anoncreds.formats.LegacyIndyCredentialFormatService

import org.slf4j.LoggerFactory

//class ProofFormatCoordinator (
//    val agent: Agent,
//    val formatServices: List<ProofFormatService<*>> = listOf<ProofFormatService<*>>(
//        AnoncredsProofFormatService(agent= agent), LegacyIndyCredentialFormatService(agent= agent)
//    )
//) {
//    private val logger = LoggerFactory.getLogger(ProofFormatCoordinator::class.java)
//}
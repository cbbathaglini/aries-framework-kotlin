package org.hyperledger.ariesframework.proofs.utils

import anoncreds_uniffi.Credential
import anoncreds_uniffi.CredentialConversions
import kotlinx.serialization.json.Json
import org.hyperledger.ariesframework.vc.model.W3cJsonLdVerifiableCredential
import org.hyperledger.ariesframework.vc.repository.W3cCredentialRecord
import org.slf4j.LoggerFactory

class W3cUtils {
    companion object {
        private val logger = LoggerFactory.getLogger(W3cUtils::class.java)
        fun getCredentialUniffiByW3cCredentialRecord(credentialRecord: W3cCredentialRecord): Credential {
            val cred = credentialRecord.credential.toJson()
            val jsonld: W3cJsonLdVerifiableCredential = W3cJsonLdVerifiableCredential.fromJson(cred)
            val w3cJsonLdVerifiableCredentialStr = Json.encodeToString(jsonld)
            var w3cJsonLdVerifiableCredentialStrClean =
                w3cJsonLdVerifiableCredentialStr.replace("\\\"", "")
            w3cJsonLdVerifiableCredentialStrClean =
                Regex("\"credentialSubject\"\\s*:\\s*\\[(\\{.*?\\})\\]")
                    .replace(w3cJsonLdVerifiableCredentialStrClean) { matchResult ->
                        val inner = matchResult.groupValues[1]
                        "\"credentialSubject\": $inner"
                    }

            // logger.info("w3cJsonLdVerifiableCredentialStr PROOF: $w3cJsonLdVerifiableCredentialStrClean ")
            return CredentialConversions().credentialFromW3cJson(
                w3cJsonLdVerifiableCredentialStrClean,
            )
        }
    }
}

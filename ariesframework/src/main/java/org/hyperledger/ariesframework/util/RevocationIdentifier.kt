package org.hyperledger.ariesframework.util

class RevocationIdentifier {
    companion object {
        // indyRevocationIdentifier = <revocation_registry_id>::<credential_revocation_id>
        // ThreadID = indy::<revocation_registry_id>::<credential_revocation_id>
        val v1ThreadRegex = Regex(
            """(indy)::((?:[\dA-Za-z]{21,22}):4:(?:[\dA-Za-z]{21,22}):3:[Cc][Ll]:(?:(?:[1-9][0-9]*)|(?:[\dA-Za-z]{21,22}:2:.+:[0-9.]+)):.+?:CL_ACCUM:(?:[\dA-Za-z-]+))::(\d+)$"""
        )

        // CredentialID = <revocation_registry_id>::<credential_revocation_id>
        val v2IndyRevocationIdentifierRegex = Regex(
            """((?:[\dA-Za-z]{21,22}):4:(?:[\dA-Za-z]{21,22}):3:[Cc][Ll]:(?:(?:[1-9][0-9]*)|(?:[\dA-Za-z]{21,22}:2:.+:[0-9.]+)):.+?:CL_ACCUM:(?:[\dA-Za-z-]+))::(\d+)$"""
        )

        val v2IndyRevocationFormat = "indy-anoncreds"

        // CredentialID = <revocation_registry_id>::<credential_revocation_id>
        val v2AnonCredsRevocationIdentifierRegex = Regex(
            """([a-zA-Z0-9+\-.]+:.+)::(\d+)$"""
        )

        val v2AnonCredsRevocationFormat = "anoncreds"
    }
}
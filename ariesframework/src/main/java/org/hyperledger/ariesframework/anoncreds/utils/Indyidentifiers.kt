package org.hyperledger.ariesframework.anoncreds.utils

import java.util.regex.Pattern

object IndyIdentifierUtils {

    private val didIndyAnonCredsBase =
        Pattern.compile("(did:indy:((?:[a-z][_a-z0-9-]*)(?::[a-z][_a-z0-9-]*)?):([1-9A-HJ-NP-Za-km-z]{21,22}))/anoncreds/v0/")

    private val unqualifiedSchemaIdRegex =
        Pattern.compile("^([a-zA-Z0-9]{21,22}):2:(.+):([0-9.]+)$")

    private val didIndySchemaIdRegex =
        Pattern.compile("${didIndyAnonCredsBase.pattern()}/SCHEMA/(.+)/([0-9.]+)")

    private val unqualifiedCredentialDefinitionIdRegex =
        Pattern.compile("^([a-zA-Z0-9]{21,22}):3:CL:([1-9][0-9]*):(.+)$")

    private val didIndyCredentialDefinitionIdRegex =
        Pattern.compile("${didIndyAnonCredsBase.pattern()}/CLAIM_DEF/([1-9][0-9]*)/(.+)")

    private val unqualifiedRevocationRegistryIdRegex =
        Pattern.compile("^([a-zA-Z0-9]{21,22}):4:[a-zA-Z0-9]{21,22}:3:CL:([1-9][0-9]*):(.+):CL_ACCUM:(.+)$")

    private val didIndyRevocationRegistryIdRegex =
        Pattern.compile("${didIndyAnonCredsBase.pattern()}/REV_REG_DEF/([1-9][0-9]*)/(.+)/(.+)")

    private val didIndyRegex =
        Pattern.compile("^did:indy:((?:[a-z][_a-z0-9-]*)(?::[a-z][_a-z0-9-]*)?):([1-9A-HJ-NP-Za-km-z]{21,22})$")

    fun getUnqualifiedSchemaId(unqualifiedDid: String, name: String, version: String): String {
        return "$unqualifiedDid:2:$name:$version"
    }

    fun getUnqualifiedCredentialDefinitionId(unqualifiedDid: String, schemaSeqNo: String, tag: String): String {
        return "$unqualifiedDid:3:CL:$schemaSeqNo:$tag"
    }

    fun getUnqualifiedRevocationRegistryDefinitionId(
        unqualifiedDid: String,
        schemaSeqNo: String,
        credentialDefinitionTag: String,
        revocationRegistryTag: String
    ): String {
        return "$unqualifiedDid:4:$unqualifiedDid:3:CL:$schemaSeqNo:$credentialDefinitionTag:CL_ACCUM:$revocationRegistryTag"
    }

    fun isUnqualifiedIndyDid(did: String): Boolean {
        return unqualifiedSchemaIdRegex.matcher(did).matches()
    }

    fun isUnqualifiedCredentialDefinitionId(credentialDefinitionId: String): Boolean {
        return unqualifiedCredentialDefinitionIdRegex.matcher(credentialDefinitionId).matches()
    }

    fun isUnqualifiedRevocationRegistryId(revocationRegistryId: String): Boolean {
        return unqualifiedRevocationRegistryIdRegex.matcher(revocationRegistryId).matches()
    }

    fun isDidIndySchemaId(schemaId: String): Boolean {
        return didIndySchemaIdRegex.matcher(schemaId).matches()
    }

    fun isDidIndyCredentialDefinitionId(credentialDefinitionId: String): Boolean {
        return didIndyCredentialDefinitionIdRegex.matcher(credentialDefinitionId).matches()
    }

    fun isDidIndyRevocationRegistryId(revocationRegistryId: String): Boolean {
        return didIndyRevocationRegistryIdRegex.matcher(revocationRegistryId).matches()
    }

    fun parseIndyDid(did: String): Pair<String, String> {
        val match = didIndyRegex.matcher(did)
        if (match.matches()) {
            return Pair(match.group(1) ?: "", match.group(2) ?: "")
        } else {
            throw IllegalArgumentException("$did is not a valid did:indy DID")
        }
    }

    fun isIndyDid(identifier: String): Boolean {
        return identifier.startsWith("did:indy:")
    }

//    fun getQualifiedDidIndyDid(identifier: String, namespace: String): String {
//        if (isIndyDid(identifier)) return identifier
//
//        if (namespace.isBlank()) {
//            throw IllegalArgumentException("Missing required indy namespace")
//        }
//
//        return when {
//            isUnqualifiedSchemaId(identifier) -> {
//                val (namespaceIdentifier, schemaName, schemaVersion) = parseIndySchemaId(identifier)
//                "did:indy:$namespace:$namespaceIdentifier/anoncreds/v0/SCHEMA/$schemaName/$schemaVersion"
//            }
//            isUnqualifiedCredentialDefinitionId(identifier) -> {
//                val (namespaceIdentifier, schemaSeqNo, tag) = parseIndyCredentialDefinitionId(identifier)
//                "did:indy:$namespace:$namespaceIdentifier/anoncreds/v0/CLAIM_DEF/$schemaSeqNo/$tag"
//            }
//            isUnqualifiedRevocationRegistryId(identifier) -> {
//                val (namespaceIdentifier, schemaSeqNo, credentialDefinitionTag, revocationRegistryTag) =
//                    parseIndyRevocationRegistryId(identifier)
//                "did:indy:$namespace:$namespaceIdentifier/anoncreds/v0/REV_REG_DEF/$schemaSeqNo/$credentialDefinitionTag/$revocationRegistryTag"
//            }
//            isUnqualifiedIndyDid(identifier) -> {
//                "did:indy:$namespace:$identifier"
//            }
//            else -> throw IllegalArgumentException("Cannot create qualified indy identifier for '$identifier' with namespace '$namespace'")
//        }
//    }

    fun parseIndySchemaId(schemaId: String): Triple<String, String, String> {
        val didIndyMatch = didIndySchemaIdRegex.matcher(schemaId)
        if (didIndyMatch.matches()) {
            return Triple(didIndyMatch.group(2) ?: "", didIndyMatch.group(3) ?: "", didIndyMatch.group(4) ?: "")
        }

        val legacyMatch = unqualifiedSchemaIdRegex.matcher(schemaId)
        if (legacyMatch.matches()) {
            return Triple(legacyMatch.group(1) ?: "", legacyMatch.group(2) ?: "", legacyMatch.group(3) ?: "")
        }

        throw IllegalArgumentException("Invalid schema id: $schemaId")
    }

    fun parseIndyCredentialDefinitionId(credentialDefinitionId: String): Triple<String, String, String> {
        val didIndyMatch = didIndyCredentialDefinitionIdRegex.matcher(credentialDefinitionId)
        if (didIndyMatch.matches()) {
            return Triple(didIndyMatch.group(2) ?: "", didIndyMatch.group(3) ?: "", didIndyMatch.group(4) ?: "")
        }

        val legacyMatch = unqualifiedCredentialDefinitionIdRegex.matcher(credentialDefinitionId)
        if (legacyMatch.matches()) {
            return Triple(legacyMatch.group(1) ?: "", legacyMatch.group(2) ?: "", legacyMatch.group(3) ?: "")
        }

        throw IllegalArgumentException("Invalid credential definition id: $credentialDefinitionId")
    }

//    fun parseIndyRevocationRegistryId(revocationRegistryId: String): Quadruple<String, String, String, String> {
//        val didIndyMatch = didIndyRevocationRegistryIdRegex.matcher(revocationRegistryId)
//        if (didIndyMatch.matches()) {
//            return Quadruple(
//                didIndyMatch.group(2) ?: "",
//                didIndyMatch.group(3) ?: "",
//                didIndyMatch.group(4) ?: "",
//                didIndyMatch.group(5) ?: ""
//            )
//        }
//
//        val legacyMatch = unqualifiedRevocationRegistryIdRegex.matcher(revocationRegistryId)
//        if (legacyMatch.matches()) {
//            return Quadruple(
//                legacyMatch.group(1) ?: "",
//                legacyMatch.group(2) ?: "",
//                legacyMatch.group(3) ?: "",
//                legacyMatch.group(4) ?: ""
//            )
//        }
//
//        throw IllegalArgumentException("Invalid revocation registry id: $revocationRegistryId")
//    }
}
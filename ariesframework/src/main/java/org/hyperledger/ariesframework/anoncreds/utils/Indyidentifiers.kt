package org.hyperledger.ariesframework.anoncreds.utils

import org.hyperledger.ariesframework.anoncreds.model.AnonCredsCredentialDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsSchema
import org.hyperledger.ariesframework.anoncreds.model.StoreCredential
import org.slf4j.LoggerFactory
import java.util.regex.Pattern
import kotlin.math.log

object Indyidentifiers {
    private val logger = LoggerFactory.getLogger(Indyidentifiers::class.java)

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
        revocationRegistryTag: String,
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

    fun isUnqualifiedSchemaId(schemaId: String): Boolean {
        return unqualifiedSchemaIdRegex.matcher(schemaId).matches()
    }

    fun isUnqualifiedDidIndyRevocationRegistryDefinition(
        revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition
    ): Boolean {
        return isUnqualifiedIndyDid(revocationRegistryDefinition.issuerId) &&
                isUnqualifiedCredentialDefinitionId(revocationRegistryDefinition.credDefId)
    }


    fun isUnqualifiedDidIndyCredentialDefinition(credentialDefinition: AnonCredsCredentialDefinition): Boolean {
        return isUnqualifiedIndyDid(credentialDefinition.issuerId) &&
                isUnqualifiedSchemaId(credentialDefinition.schemaId)
    }

    fun getQualifiedDidIndyDid(identifier: String, namespace: String): String {
        if (isIndyDid(identifier)) return identifier

        //adicionar pq nao ta funfando
        if (namespace.isBlank()) {
            throw IllegalArgumentException("Missing required indy namespace")
        }

        return when {
            isUnqualifiedSchemaId(identifier) -> {
                logger.info("isUnqualifiedSchemaId")
                val (namespaceIdentifier, schemaName, schemaVersion) = parseIndySchemaId(identifier)
                "did:indy:$namespace:$namespaceIdentifier/anoncreds/v0/SCHEMA/$schemaName/$schemaVersion"
            }
            isUnqualifiedCredentialDefinitionId(identifier) -> {
                logger.info("isUnqualifiedCredentialDefinitionId")
                val (namespaceIdentifier, schemaSeqNo, tag) = parseIndyCredentialDefinitionId(identifier)
                "did:indy:$namespace:$namespaceIdentifier/anoncreds/v0/CLAIM_DEF/$schemaSeqNo/$tag"
            }
            isUnqualifiedRevocationRegistryId(identifier) -> {
                logger.info("isUnqualifiedRevocationRegistryId")

                val (did, namespaceIdentifier, schemaSeqNo, credentialDefinitionTag, revocationRegistryTag, namespace) =
                    parseIndyRevocationRegistryId(identifier)
                logger.info("did:indy:------:$namespaceIdentifier/anoncreds/v0/REV_REG_DEF/$schemaSeqNo/$credentialDefinitionTag/$revocationRegistryTag")
                "did:indy:$namespace:$namespaceIdentifier/anoncreds/v0/REV_REG_DEF/$schemaSeqNo/$credentialDefinitionTag/$revocationRegistryTag"
            }
            isUnqualifiedIndyDid(identifier) -> {
                logger.info("isUnqualifiedIndyDid")
                "did:indy:$namespace:$identifier"
            }
            else -> throw IllegalArgumentException("Cannot create qualified indy identifier for '$identifier' with namespace '$namespace'")
        }
    }

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

    fun isQualifiedDidIndyCredentialDefinition(
        credentialDefinition: AnonCredsCredentialDefinition
    ): Boolean {
        return !isUnqualifiedIndyDid(credentialDefinition.issuerId) &&
                !isUnqualifiedSchemaId(credentialDefinition.schemaId)
    }

    fun isUnqualifiedDidIndySchema(schema: AnonCredsSchema): Boolean {
        return isUnqualifiedIndyDid(schema.issuerId)
    }

    fun getQualifiedDidIndyCredentialDefinition(
        credentialDefinition: AnonCredsCredentialDefinition,
        namespace: String
    ): AnonCredsCredentialDefinition {
        if (isQualifiedDidIndyCredentialDefinition(credentialDefinition)) {
            return credentialDefinition.copy()
        }

        return credentialDefinition.copy(
            issuerId = getQualifiedDidIndyDid(credentialDefinition.issuerId, namespace),
            schemaId = getQualifiedDidIndyDid(credentialDefinition.schemaId, namespace)
        )
    }

    fun getQualifiedDidIndyRevocationRegistryDefinition(
        revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition,
        namespace: String
    ): AnonCredsRevocationRegistryDefinition {
        return if (isQualifiedRevocationRegistryDefinition(revocationRegistryDefinition)) {
            revocationRegistryDefinition
        } else {
            revocationRegistryDefinition.copy(
                issuerId = getQualifiedDidIndyDid(revocationRegistryDefinition.issuerId, namespace),
                credDefId = getQualifiedDidIndyDid(revocationRegistryDefinition.credDefId, namespace)
            )
        }
    }

    fun isQualifiedRevocationRegistryDefinition(
        revocationRegistryDefinition: AnonCredsRevocationRegistryDefinition
    ): Boolean {
        return !isUnqualifiedIndyDid(revocationRegistryDefinition.issuerId) &&
                !isUnqualifiedCredentialDefinitionId(revocationRegistryDefinition.credDefId)
    }

    fun isQualifiedDidIndySchema(schema: AnonCredsSchema): Boolean {
        return !isUnqualifiedIndyDid(schema.issuerId)
    }

    fun getQualifiedDidIndySchema(schema: AnonCredsSchema, namespace: String): AnonCredsSchema {
        return if (isQualifiedDidIndySchema(schema)) {
            schema
        } else {
            schema.copy(
                issuerId = getQualifiedDidIndyDid(schema.issuerId, namespace)
            )
        }
    }

    fun parseIndyRevocationRegistryId(revocationRegistryId: String): ParsedIndyRevocationRegistryId {
        logger.info("parseIndyRevocationRegistryId:::: $revocationRegistryId")
        val didIndyMatch = didIndyRevocationRegistryIdRegex.matcher(revocationRegistryId)
        logger.info("didIndyMatch1:::: ${didIndyMatch.toString()}")

        if (didIndyMatch != null) {

            val did = didIndyMatch.group(1)
            val namespace = didIndyMatch.group(2)
            val namespaceIdentifier = didIndyMatch.group(3)
            val schemaSeqNo = didIndyMatch.group(4)
            val credentialDefinitionTag = didIndyMatch.group(5)
            val revocationRegistryTag = didIndyMatch.group(6)

            logger.info("namespace: $namespace")
            return ParsedIndyRevocationRegistryId(
                did = did!!,
                namespaceIdentifier = namespaceIdentifier!!,
                schemaSeqNo = schemaSeqNo!!,
                credentialDefinitionTag = credentialDefinitionTag!!,
                revocationRegistryTag = revocationRegistryTag!!,
                namespace = namespace
            )
        }

        val legacyMatch = unqualifiedRevocationRegistryIdRegex.matcher(revocationRegistryId)
        logger.info("legacyMatch:::: ${legacyMatch.toString()}")
        if (legacyMatch != null) {
            logger.info("legacyMatch gorup 1:::: ${legacyMatch.group(1)}")

            val did = legacyMatch.group(1)
            val schemaSeqNo = legacyMatch.group(4)
            val credentialDefinitionTag = legacyMatch.group(5)
            val revocationRegistryTag = legacyMatch.group(6)

            return ParsedIndyRevocationRegistryId(
                did = did,
                namespaceIdentifier = did,
                schemaSeqNo = schemaSeqNo,
                credentialDefinitionTag = credentialDefinitionTag,
                revocationRegistryTag = revocationRegistryTag
            )
        }

        throw IllegalArgumentException("Invalid revocation registry id: $revocationRegistryId")
    }

    fun getUnqualifiedDidIndyDid(identifier: String): String {
        return when {
            isUnqualifiedIndyDid(identifier) -> identifier

            isDidIndySchemaId(identifier) -> {
                val (namespaceIdentifier, schemaName, schemaVersion) = parseIndySchemaId(identifier)
                getUnqualifiedSchemaId(namespaceIdentifier, schemaName, schemaVersion)
            }

            isDidIndyCredentialDefinitionId(identifier) -> {
                val (namespaceIdentifier, schemaSeqNo, tag) = parseIndyCredentialDefinitionId(identifier)
                getUnqualifiedCredentialDefinitionId(namespaceIdentifier, schemaSeqNo, tag)
            }

            isDidIndyRevocationRegistryId(identifier) -> {
                val (namespaceIdentifier, schemaSeqNo, credentialDefinitionTag, revocationRegistryTag) =
                    parseIndyRevocationRegistryId(identifier)
                getUnqualifiedRevocationRegistryDefinitionId(
                    namespaceIdentifier,
                    schemaSeqNo,
                    credentialDefinitionTag,
                    revocationRegistryTag
                )
            }

            else -> {
                val (namespaceIdentifier, _) = parseIndyDid(identifier)
                namespaceIdentifier
            }
        }
    }
}

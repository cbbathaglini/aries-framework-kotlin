package org.hyperledger.ariesframework.ledger

class SchemaTemplate(val name: String, val version: String, val attributes: List<String>)

class CredentialDefinitionTemplate(
    val schema: String,
    val tag: String,
    val supportRevocation: Boolean,
    val seqNo: Int,
)
class RevocationRegistryDefinitionTemplate(
    val credDefId: String,
    val tag: String,
    val maxCredNum: Int,
    val tailsDirPath: String? = null,
)

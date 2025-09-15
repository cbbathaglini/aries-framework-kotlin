package org.hyperledger.ariesframework.util.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.RevocationRegistryValue

object RevocationRegistryDefinitionSerializer :
    KSerializer<uniffi.indy_besu_vdr.RevocationRegistryDefinition> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("RevocationRegistryDefinition")

    override fun serialize(
        encoder: Encoder,
        value: uniffi.indy_besu_vdr.RevocationRegistryDefinition,
    ) {
        // value.value é String (JSON). Precisamos tipar.
        val revRegValue: RevocationRegistryValue =
            try {
                // Json.decodeFromString(RevocationRegistryValue.serializer(), value.value)
                Json.decodeFromString<RevocationRegistryValue>(value.value)
            } catch (e: Exception) {
                throw SerializationException(
                    "Failed to parse RevocationRegistryValue from 'value' string",
                    e,
                )
            }

        val dto = AnonCredsRevocationRegistryDefinition(
            issuerId = value.issuerId,
            revocDefType = value.revocDefType,
            credDefId = value.credDefId,
            tag = value.tag,
            value = revRegValue,
        )

        // encoder.encodeSerializableValue(AnonCredsRevocationRegistryDefinition.serializer(), dto)

        encoder.encodeSerializableValue(serializer<AnonCredsRevocationRegistryDefinition>(), dto)
    }

    override fun deserialize(decoder: Decoder): uniffi.indy_besu_vdr.RevocationRegistryDefinition {
        throw NotImplementedError("Only serialization is supported")
    }
}

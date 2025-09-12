package org.hyperledger.ariesframework.util.serializer

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationRegistryDefinition
import org.hyperledger.ariesframework.anoncreds.model.AnonCredsRevocationStatusList


object RevocationStatusListSerializer :
    KSerializer<uniffi.indy_besu_vdr.RevocationStatusList> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("RevocationStatusList")

    override fun serialize(encoder: Encoder, value: uniffi.indy_besu_vdr.RevocationStatusList) {
        val dto = AnonCredsRevocationStatusList(
            issuerId = value.issuerId,
            revRegDefId = value.revRegDefId,
            revocationList = value.revocationList.map { it.toInt() },
            currentAccumulator = value.currentAccumulator,
            timestamp = value.timestamp.toLong()
        )
        //encoder.encodeSerializableValue(AnonCredsRevocationStatusList.serializer(), dto)
        encoder.encodeSerializableValue(serializer<AnonCredsRevocationStatusList>(), dto)

    }

    override fun deserialize(decoder: Decoder): uniffi.indy_besu_vdr.RevocationStatusList {
        throw NotImplementedError("Only serialization is supported")
    }
}



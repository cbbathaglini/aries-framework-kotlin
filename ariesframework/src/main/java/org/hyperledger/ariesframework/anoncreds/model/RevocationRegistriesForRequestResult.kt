package org.hyperledger.ariesframework.anoncreds.model

import org.hyperledger.ariesframework.anoncreds.formats.anoncreds.AnonCredsSelectedCredentials


data class RevocationRegistriesForRequestResult(
    val revocationRegistries: MutableMap<String, RevocationRegistryBucket>,
    val updatedSelectedCredentials: AnonCredsSelectedCredentials
)
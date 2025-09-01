package org.hyperledger.ariesframework.credentials.formats.anoncreds

class MetadataKeys {
    companion object {
        /**
         * Metadata key for strong metadata on an AnonCreds credential.
         *
         * MUST be used with {@link AnonCredsCredentialMetadata}
         */
        const val AnonCredsCredentialMetadataKey = "_anoncreds/credential"

        /**
         * Metadata key for storing metadata on an AnonCreds credential request.
         *
         * MUST be used with {@link AnonCredsCredentialRequestMetadata}
         */
        const val AnonCredsCredentialRequestMetadataKey = "_anoncreds/credentialRequest"

        /**
         * Metadata key for storing the W3C AnonCreds credential metadata.
         *
         * MUST be used with {@link W3cAnonCredsCredentialMetadata}
         */
        const val W3cAnonCredsCredentialMetadataKey = "_w3c/anonCredsMetadata"
    }
}

package org.hyperledger.ariesframework.anoncreds.service.tails

interface TailsFileService {

    /**
     * Retrieve base directory for tail file storage
     */
    suspend fun getTailsBasePath(): String

    /**
     * Upload the tails file for a given revocation registry definition.
     *
     * Optionally, receives revocationRegistryDefinitionId in case the ID is known beforehand.
     * Returns the published tail file URL.
     */
    suspend fun uploadTailsFile(
        options: UploadTailsFileOptions,
    ): UploadTailsFileResult

    /**
     * Retrieve the tails file for a given revocation registry, downloading it
     * from the tailsLocation URL if not present in internal cache.
     */
    suspend fun getTailsFile(
        options: GetTailsFileOptions,
    ): GetTailsFileResult
}

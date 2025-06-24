package org.hyperledger.ariesframework.anoncreds.service.tails

import org.hyperledger.ariesframework.agent.Agent

class BasicTailsFileService(
    private val tailsDirectoryPath: String? = null,
    val agent: Agent
) : TailsFileService {

    override suspend fun getTailsBasePath(): String {
        val fileSystem = agentContext.dependencyManager.resolve(FileSystem::class)

        val fileSystem = agent.fileSystem
        val basePath = "${tailsDirectoryPath ?: fileSystem.cachePath}/anoncreds/tails"
        if (!fileSystem.exists(basePath)) {
            fileSystem.createDirectory("$basePath/file")
        }
        return basePath
    }

    override suspend fun uploadTailsFile(
        agentContext: AgentContext,
        options: UploadTailsFileOptions
    ): TailsFileUrlResult {
        throw CredoError("BasicTailsFileService only supports tails file downloading")
    }

    override suspend fun getTailsFile(
        agentContext: AgentContext,
        options: GetTailsFileOptions
    ): TailsFilePathResult {
        val (revocationRegistryDefinition) = options
        val (tailsLocation, tailsHash) = revocationRegistryDefinition.value

        val fileSystem = agentContext.dependencyManager.resolve(FileSystem::class)

        try {
            agentContext.config.logger.debug("Checking if tails file for URL $tailsLocation exists in FileSystem")

            val tailsExists = tailsFileExists(agentContext, tailsHash)
            val tailsFilePath = getTailsFilePath(agentContext, tailsHash)

            agentContext.config.logger.debug("Tails file for $tailsLocation ${if (tailsExists) "is stored" else "is not stored"} at $tailsFilePath")

            if (!tailsExists) {
                agentContext.config.logger.debug("Retrieving tails file from URL $tailsLocation")

                fileSystem.downloadToFile(
                    url = tailsLocation,
                    targetPath = tailsFilePath,
                    verifyHash = HashVerification(
                        algorithm = "sha256",
                        hash = TypedArrayEncoder.fromBase58(tailsHash)
                    )
                )

                agentContext.config.logger.debug("Saved tails file to FileSystem at path $tailsFilePath")
            }

            return TailsFilePathResult(tailsFilePath)
        } catch (e: Exception) {
            agentContext.config.logger.error("Error while retrieving tails file from URL $tailsLocation", e)
            throw e
        }
    }

    private suspend fun getTailsFilePath(agentContext: AgentContext, tailsHash: String): String {
        return "${getTailsBasePath(agentContext)}/$tailsHash"
    }

    private suspend fun tailsFileExists(agentContext: AgentContext, tailsHash: String): Boolean {
        val fileSystem = agentContext.dependencyManager.resolve(FileSystem::class)
        val tailsFilePath = getTailsFilePath(agentContext, tailsHash)
        return fileSystem.exists(tailsFilePath)
    }
}
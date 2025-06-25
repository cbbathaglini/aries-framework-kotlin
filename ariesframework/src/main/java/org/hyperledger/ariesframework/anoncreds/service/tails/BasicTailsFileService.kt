package org.hyperledger.ariesframework.anoncreds.service.tails

import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.credentials.formats.TypedArrayEncoder
import org.hyperledger.ariesframework.credentials.v2.handlers.IssueCredentialHandlerV2
import org.hyperledger.ariesframework.error.CredoError
import org.hyperledger.ariesframework.util.Base58
import org.slf4j.LoggerFactory
import java.io.File
import java.math.BigInteger
import java.net.URL
import java.security.MessageDigest

class BasicTailsFileService (agent: Agent) : TailsFileService {
    private val logger = LoggerFactory.getLogger(BasicTailsFileService::class.java)
    private val tailsDirectoryPath: String? = null

    override suspend fun getTailsBasePath(): String {
//        val fileSystem = agentContext.dependencyManager.resolve(FileSystem::class)
//
//        val fileSystem = agent.fileSystem
//        val basePath = "${tailsDirectoryPath ?: fileSystem.cachePath}/anoncreds/tails"
//        if (!fileSystem.exists(basePath)) {
//            fileSystem.createDirectory("$basePath/file")
//        }
//        return basePath

        val basePath = "${tailsDirectoryPath ?: getDefaultCachePath()}/anoncreds/tails"
        val baseDir = File(basePath)

        if (!baseDir.exists()) {
            // create dir "anoncreds/tails/file"
            File(baseDir, "file").mkdirs()
        }

        return basePath // "anoncreds/tails/file"
    }

    private fun getDefaultCachePath(): String? {
        // uses temporary path of system as cache by defaut
        return System.getProperty("java.io.tmpdir")
    }

    override suspend fun uploadTailsFile(
        options: UploadTailsFileOptions
    ): UploadTailsFileResult {
        throw CredoError("BasicTailsFileService only supports tails file downloading")
    }

    override suspend fun getTailsFile(
        options: GetTailsFileOptions
    ): GetTailsFileResult {
        val (revocationRegistryDefinition) = options
        val tailsLocation = revocationRegistryDefinition.value.tailsLocation
        val tailsHash = revocationRegistryDefinition.value.tailsHash

        try {
            logger.debug("Checking if tails file for URL $tailsLocation exists in FileSystem")

            val tailsExists = tailsFileExists(tailsHash)
            val tailsFilePath = getTailsFilePath(tailsHash)

            logger.debug("Tails file for $tailsLocation ${if (tailsExists) "is stored" else "is not stored"} at $tailsFilePath")

            if (!tailsExists) {
                logger.debug("Retrieving tails file from URL $tailsLocation")

                //[TODO] revisar
                downloadToFileWithSha256Check(
                    url= tailsLocation,
                    targetPath= tailsFilePath,
                    expectedHashBase58= tailsHash
                )

                logger.debug("Saved tails file to FileSystem at path $tailsFilePath")
            }

            return GetTailsFileResult(tailsFilePath)
        } catch (e: Exception) {
            logger.error("Error while retrieving tails file from URL $tailsLocation", e)
            throw e
        }
    }

    fun downloadToFileWithSha256Check(
        url: String,
        targetPath: String,
        expectedHashBase58: String
    ) {
        val connection = URL(url).openStream()
        val targetFile = File(targetPath)

        // Save file to disk
        targetFile.outputStream().use { output ->
            connection.use { input ->
                input.copyTo(output)
            }
        }

        // Re-open file to compute hash
        val fileBytes = targetFile.readBytes()
        val actualHash = MessageDigest.getInstance("SHA-256").digest(fileBytes)

        // Decode expected hash from Base58
        val expectedHash = Base58.decode(expectedHashBase58)

        // Compare
        if (!actualHash.contentEquals(expectedHash)) {
            throw IllegalStateException("SHA-256 hash does not match expected value.")
        }
    }

//    // [TODO] ia generate
//    fun decodeBase58(input: String): ByteArray {
//        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
//        val base = BigInteger.valueOf(58)
//
//        var result = BigInteger.ZERO
//        var leadingZeroCount = 0
//
//        for (char in input) {
//            val digit = alphabet.indexOf(char)
//            if (digit == -1) throw IllegalArgumentException("Invalid Base58 character: '$char'")
//            result = result.multiply(base).add(BigInteger.valueOf(digit.toLong()))
//        }
//
//        // Contar os caracteres '1' à esquerda (representam bytes zero no início)
//        while (leadingZeroCount < input.length && input[leadingZeroCount] == '1') {
//            leadingZeroCount++
//        }
//
//        val bytes = result.toByteArray()
//        val stripSignByte = bytes.size > 0 && bytes[0] == 0.toByte()
//        val decoded = if (stripSignByte) bytes.drop(1).toByteArray() else bytes
//
//        // Adicionar os zeros à esquerda que foram representados como '1'
//        return ByteArray(leadingZeroCount) { 0 } + decoded
//    }

    // if dont need hash verification
    fun downloadFile(url: String, targetPath: String) {
        URL(url).openStream().use { input ->
            File(targetPath).outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }


    private suspend fun getTailsFilePath(tailsHash: String): String {
        return "${getTailsBasePath()}/$tailsHash"
    }

    private suspend fun tailsFileExists(tailsHash: String): Boolean {
        val tailsFilePath = getTailsFilePath(tailsHash)
        val baseDir = File(tailsFilePath)

       return baseDir.exists()
    }
}
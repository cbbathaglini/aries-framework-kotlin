package org.hyperledger.ariesframework.storage

interface FileSystem {
    val dataPath: String
    val cachePath: String
    val tempPath: String

    suspend fun exists(path: String): Boolean
    suspend fun createDirectory(path: String)
    suspend fun copyFile(sourcePath: String, destinationPath: String)
    suspend fun write(path: String, data: String)
    suspend fun read(path: String): String
    suspend fun delete(path: String)
    suspend fun downloadToFile(url: String, path: String, options: DownloadToFileOptions? = null)
}

data class DownloadToFileOptions(
    val verifyHash: VerifyHash? = null
)

data class VerifyHash(
    val algorithm: String = "sha256",
    val hash: ByteArray
)
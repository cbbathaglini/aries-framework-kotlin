package org.hyperledger.ariesframework.ledger.ledgerIndy.contracts
import org.hyperledger.ariesframework.wallet.Wallet
import org.web3j.utils.Numeric
import uniffi.indy_besu_vdr.SignatureData
import uniffi.indy_besu_vdr.Transaction
import java.security.MessageDigest

class IndyBesuSigner(private val key: Key, private val wallet: Wallet) {

    fun computeAddress(publicKey: ByteArray): String {
        // O Web3j espera a chave pública sem o prefixo 0x04, então removemos o primeiro byte se necessário
        val uncompressedKey = if (publicKey[0] == 0x04.toByte()) publicKey.copyOfRange(1, publicKey.size) else publicKey

        // Geramos o hash Keccak-256 da chave pública
        val digest = MessageDigest.getInstance("KECCAK-256").digest(uncompressedKey)

        // Pegamos os últimos 20 bytes do hash para gerar o endereço Ethereum
        return "0x" + Numeric.toHexStringNoPrefix(digest.copyOfRange(digest.size - 20, digest.size))
    }


    val address: String = computeAddress("0x${TypedArrayEncoder.toHex(key.publicKey)}")

    suspend fun signTransaction(transaction: Transaction) {
        val bytesToSign = transaction.data
        val signature = sign(bytesToSign)

        transaction.signature =
            SignatureData(
                recoveryId = signature.yParity,
                signature = getBytes(concat(signature.r, signature.s))
            )

    }

    suspend fun sign(data: ByteArray): Signature {
        if (wallet !is AskarWallet && wallet !is AskarProfileWallet) {
            throw CredoError("Incorrect wallet type: Indy-Besu VDR currently only supports the Askar wallet")
        }

        val keyEntry = wallet.withSession { session ->
            session.fetchKey(name = key.publicKeyBase58)
        } ?: throw WalletError("Key entry not found")

        val signingKey = SigningKey(keyEntry.key.secretBytes)
        val signature = signingKey.sign(data)

        keyEntry.key.handle.free()

        return signature
    }
}


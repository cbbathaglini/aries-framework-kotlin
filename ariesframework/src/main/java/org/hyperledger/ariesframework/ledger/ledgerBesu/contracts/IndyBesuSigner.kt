package org.hyperledger.ariesframework.ledger.ledgerBesu.contracts
import org.hyperledger.ariesframework.ledger.ledgerBesu.credo.Key
import org.hyperledger.ariesframework.wallet.Wallet
import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Sign
import org.web3j.utils.Numeric
import uniffi.indy_besu_vdr.SignatureData
import uniffi.indy_besu_vdr.Transaction
import java.math.BigInteger
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

    // val address: String = computeAddress("0x${key.publicKey}")
    val address: String = key.computeEthereumAddress()

    suspend fun signTransaction(transaction: Transaction) {
        val bytesToSign = transaction.data
        val signature = sign(bytesToSign)
        // Supondo que `signature.r` e `signature.s` sejam do tipo BigInteger
        val r = BigInteger(1, signature.r)
        val s = BigInteger(1, signature.s)
        val vByteArray = signature.v

        // Convertendo o byte para inteiro e, em seguida, para ULong
        val v: Int = vByteArray[0].toInt() and 0xFF // Convertendo o byte para inteiro
        val yParity: ULong = if (v == 27) 0UL else 1UL
        val rBytes = Numeric.toBytesPadded(r, 32) // 32 bytes
        val sBytes = Numeric.toBytesPadded(s, 32) // 32 bytes

        val signatureBytes: ByteArray = rBytes + sBytes

        transaction.signature =
            SignatureData(
                recoveryId = yParity,
                signature = signatureBytes,
            )
    }

    suspend fun sign(data: ByteArray): Sign.SignatureData {
        /*if (wallet !is AskarWallet && wallet !is AskarProfileWallet) {
            throw CredoError("Incorrect wallet type: Indy-Besu VDR currently only supports the Askar wallet")
        }

        val keyEntry = wallet.withSession { session ->
            session.fetchKey(name = key.publicKeyBase58)
        } ?: throw WalletError("Key entry not found")*/

        // val signingKey = SigningKey(keyEntry.key.secretBytes)
        // val signingKey = SigningKey(this.wallet.linkSecretId)
        // val signature = signingKey.sign(data)

        // keyEntry.key.handle.free()

        // return signature

        val privateKeyBigInt = BigInteger(this.wallet.linkSecretId, 16)

        // 2. Criar um ECKeyPair a partir da chave privada
        val keyPair = ECKeyPair(privateKeyBigInt, Sign.publicKeyFromPrivate(privateKeyBigInt))

        // 3. Assinar os dados usando ECDSA
        return Sign.signMessage(data, keyPair, false)
    }
}

package org.hyperledger.ariesframework.ledger.ledgerBesu.credo

import org.hyperledger.ariesframework.util.Base58
import org.web3j.crypto.Keys
import org.web3j.utils.Numeric
import java.util.Base64

enum class KeyType(val value: String, val supportsEncryption: Boolean, val supportsSigning: Boolean) {
    Ed25519("ed25519", supportsEncryption = false, supportsSigning = true),
    Bls12381g1g2("bls12381g1g2", supportsEncryption = true, supportsSigning = true),
    Bls12381g1("bls12381g1", supportsEncryption = false, supportsSigning = true),
    Bls12381g2("bls12381g2", supportsEncryption = true, supportsSigning = false),
    X25519("x25519", supportsEncryption = true, supportsSigning = false),
    P256("p256", supportsEncryption = true, supportsSigning = true),
    P384("p384", supportsEncryption = true, supportsSigning = true),
    P521("p521", supportsEncryption = true, supportsSigning = true),
    K256("k256", supportsEncryption = true, supportsSigning = true),
    ;

    companion object {
        fun fromValue(value: String): KeyType? {
            return values().find { it.value == value }
        }
    }
}

class Key(
    val publicKey: ByteArray,
    val keyType: KeyType,
) {

    companion object {
        fun fromPublicKey(publicKey: ByteArray, keyType: KeyType): Key {
            return Key(publicKey, keyType)
        }

        fun fromPublicKeyBase58(publicKey: String, keyType: KeyType): Key {
            val decodedKey = Base58.decode(publicKey)
            return Key(decodedKey, keyType)
        }

        fun fromFingerprint(fingerprint: String): Key {
            val decodedKey = Base64.getDecoder().decode(fingerprint)
            return Key(decodedKey, KeyType.K256) // Substitua KeyType.DEFAULT pelo correto
        }
    }

    public fun computeEthereumAddress(): String {
        val publicKeyHex = Numeric.toHexStringNoPrefix(publicKey) // Converte ByteArray para Hex
        return Keys.getAddress(publicKeyHex) // Obtém o endereço Ethereum
    }

    val prefixedPublicKey: ByteArray
        get() = byteArrayOf(0x04) + publicKey // Exemplo de prefixação

    val fingerprint: String
        get() = Base64.getEncoder().encodeToString(publicKey)

    val publicKeyBase58: String
        get() = Base58.encode(publicKey)

    val supportsEncrypting: Boolean
        get() = keyType.supportsEncryption

    val supportsSigning: Boolean
        get() = keyType.supportsSigning

    override fun toString(): String {
        return "Key(publicKey=${Numeric.toHexString(publicKey)}, keyType=$keyType)"
    }
}

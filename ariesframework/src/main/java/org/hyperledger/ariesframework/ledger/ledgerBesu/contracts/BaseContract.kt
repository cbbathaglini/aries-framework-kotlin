package org.hyperledger.ariesframework.ledger.ledgerIndy.contracts

import kotlinx.coroutines.runBlocking
import org.hyperledger.ariesframework.ledger.ledgerIndy.contracts.IndyBesuSigner.IndyBesuSigner
import uniffi.indy_besu_vdr.LedgerClient
import uniffi.indy_besu_vdr.Transaction

class BaseContract(private val client: LedgerClient) {

    suspend fun signAndSubmit(transaction: Transaction, signer: IndyBesuSigner): String {
        signer.signTransaction(transaction)
        val transactionHash = client.submitTransaction(transaction)
        return client.getReceipt(transactionHash)
    }
}

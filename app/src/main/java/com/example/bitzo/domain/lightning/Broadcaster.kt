package com.example.bitzo.domain.lightning

import android.util.Log
import com.example.bitzo.utils.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bitcoindevkit.Transaction
import com.example.bitzo.domain.bitcoin.Wallet
import org.ldk.structs.BroadcasterInterface

// To create a transaction broadcaster we need provide an object that implements the BroadcasterInterface
// which has 1 function broadcast_transaction(tx: ByteArray?)
object LDKBroadcaster : BroadcasterInterface.BroadcasterInterfaceInterface {
    @OptIn(ExperimentalUnsignedTypes::class)
    override fun broadcast_transactions(txs: Array<out ByteArray>??) {
        txs?.let { transactions ->
            CoroutineScope(Dispatchers.IO).launch {
                transactions.forEach { txByteArray ->
                    val uByteArray = txByteArray.toUByteArray()
                    val transaction = Transaction(uByteArray.toList())

                    Wallet.broadcast(transaction)

                    Log.i("BROADCASTER", "The raw transaction broadcast is: ${txByteArray.toString()}")
                }
            }
        } ?: throw(IllegalStateException("Broadcaster attempted to broadcast a null transaction"))
    }

}
package com.example.bitzo.domain.lightning

import org.ldk.structs.Filter
import org.ldk.structs.WatchedOutput

// Filter allows LDK to let you know what transactions you should filter blocks for. This is
// useful if you pre-filter blocks or use compact filters. Otherwise, LDK will need full blocks.
object LDKTxFilter : Filter.FilterInterface {
    var txids: Array<ByteArray> = arrayOf()
    var outputs: Array<WatchedOutput> = arrayOf()

    override fun register_tx(txid: ByteArray, script_pubkey: ByteArray) {
        val txId = txid.reversedArray().toHex()
        val scriptPubkey = script_pubkey.toHex()

        txids.plus(txid)
    }

    override fun register_output(output: WatchedOutput) {
        val index = output._outpoint._index.toString()
        val scriptPubkey = output._script_pubkey.toHex()

        outputs.plus(output)
    }
}
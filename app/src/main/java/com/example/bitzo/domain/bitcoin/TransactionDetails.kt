package com.example.bitzo.domain.bitcoin

import com.example.bitzo.utils.TxType
import org.rustbitcoin.bitcoin.Amount
import org.rustbitcoin.bitcoin.FeeRate

data class TransactionDetails(
    val txid: String,
    val sent: Amount,
    val received: Amount,
    val paymentAmount: ULong,
    val fee: Amount,
    val feeRate: FeeRate,
    val txType: TxType,
    val chainPosition: ChainPosition
)

sealed interface ChainPosition {
    data object Unconfirmed : ChainPosition
    data class Confirmed(val height: UInt, val timestamp: ULong) : ChainPosition
}
package com.example.bitzo

import android.util.Log
import com.example.bitzo.domain.bitcoin.Wallet
import com.example.bitzo.domain.lightning.LDKBroadcaster
import com.example.bitzo.domain.lightning.LDKKeysManager
import com.example.bitzo.domain.lightning.Node
import com.example.bitzo.domain.lightning.Node.channelManager
import com.example.bitzo.domain.lightning.toHex
import com.example.bitzo.domain.lightning.write
import org.bitcoindevkit.Address
import org.ldk.batteries.ChannelManagerConstructor
import org.ldk.structs.ClosureReason
import org.ldk.structs.Event
import org.ldk.structs.Option_ThirtyTwoBytesZ
import org.ldk.structs.Option_u32Z
import org.ldk.structs.Result_NoneAPIErrorZ
import org.ldk.structs.Result_NoneReplayEventZ
import org.ldk.structs.Result_TransactionNoneZ
import org.ldk.structs.TxOut
import org.ldk.util.UInt128
import kotlin.random.Random

const val LDKTAG = "EVENTS"

// Responsible for backing up channel_manager bytes
object LDKEventHandler : ChannelManagerConstructor.EventHandler {
    override fun handle_event(event: Event): Result_NoneReplayEventZ {
        Log.i(LDKTAG, "Getting ready to handle event")
        handleEvent(event)
        return Result_NoneReplayEventZ.ok()
    }

    override fun persist_manager(channelManagerBytes: ByteArray?) {
        if (channelManagerBytes != null) {
            Log.i(LDKTAG, "persist-manager")
            val identifier = "channel-manager.bin"
            write(identifier, channelManagerBytes)
        }
    }

    override fun persist_network_graph(networkGraph: ByteArray?) {
        if (networkGraph !== null) {
            Log.i(LDKTAG, "persist-network-graph")
            val identifier = "network-graph.bin"
            write(identifier, networkGraph)
        }
    }

    override fun persist_scorer(scorer: ByteArray?) {
        if (scorer !== null) {
            Log.i(LDKTAG, "persist-scorer")
            val identifier = "scorer.bin"
            write(identifier, scorer)
        }
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
fun handleEvent(event: Event) {
    if (event is Event.FundingGenerationReady) {
        Log.i(LDKTAG, "FundingGenerationReady")
        if (event.output_script.size == 34 && event.output_script[0].toInt() == 0 && event.output_script[1].toInt() == 32) {
            val rawTx =
                Wallet.buildFundingTx(event.channel_value_satoshis, event.output_script)

            try {
                val fundingTx = channelManager!!.funding_transaction_generated(
                    event.temporary_channel_id,
                    event.counterparty_node_id,
                    rawTx.serialize().toUByteArray().toByteArray()
                )
                if (fundingTx is Result_NoneAPIErrorZ.Result_NoneAPIErrorZ_OK) {
                    Log.i(LDKTAG, "Funding transaction generated")
                }
                if (fundingTx is Result_NoneAPIErrorZ.Result_NoneAPIErrorZ_Err) {
                    Log.i(LDKTAG, "Error creating funding transaction: ${fundingTx.err}")
                }
            } catch (e: Exception) {
                Log.i(LDKTAG, "Error: ${e.message}")
            }

        }
    }

    if (event is Event.OpenChannelRequest) {
        Log.i(LDKTAG, "Event.OpenChannelRequest")

        val userChannelId = UInt128(Random.nextLong(0, 100))

        val res = channelManager!!.accept_inbound_channel(
            event.temporary_channel_id,
            event.counterparty_node_id,
            userChannelId
        )

        if (res != null) {
            if (res.is_ok) {
                Log.i(LDKTAG, "Open Channel Request Accepted")
            } else {
                Log.i(LDKTAG, "Open Channel Request Rejected")
            }
        }
    }

    if (event is Event.ChannelClosed) {
        Log.i(LDKTAG, "ChannelClosed")
        val reason = event.reason

        if (reason is ClosureReason.CommitmentTxConfirmed) {
            Log.i(LDKTAG, "CommitmentTxConfirmed")
        }
        if (reason is ClosureReason.LegacyCooperativeClosure) {
            Log.i(LDKTAG, "LegacyCooperativeClosure")
        }
        if (reason is ClosureReason.CounterpartyForceClosed) {
            Log.i(LDKTAG, "CounterpartyForceClosed")
        }
        if (reason is ClosureReason.DisconnectedPeer) {
            Log.i(LDKTAG, "DisconnectedPeer")
        }
        if (reason is ClosureReason.HolderForceClosed) {
            Log.i(LDKTAG, "HolderForceClosed")
        }
        if (reason is ClosureReason.OutdatedChannelManager) {
            Log.i(LDKTAG, "OutdatedChannelManager")
        }
        if (reason is ClosureReason.ProcessingError) {
            Log.i(LDKTAG, "ProcessingError")
        }
    }

    if (event is Event.ChannelPending) {
        Log.i(LDKTAG, "Event.ChannelPending")
    }

    if (event is Event.ChannelReady) {
        Log.i(LDKTAG, "Event.ChannelReady")
    }

    if (event is Event.PaymentSent) {
        Log.i(LDKTAG, "Payment Sent")
    }

    if (event is Event.PaymentFailed) {
        Log.i(LDKTAG, "Payment Failed")
    }

    if (event is Event.PaymentPathFailed) {
        Log.i(LDKTAG, "Event.PaymentPathFailed${event.failure}")
    }

    if (event is Event.PendingHTLCsForwardable) {
        Log.i(LDKTAG, "Event.PendingHTLCsForwardable")
        channelManager!!.process_pending_htlc_forwards()
    }

    if (event is Event.SpendableOutputs) {
        Log.i(LDKTAG, "Event.SpendableOutputs")
//        val outputs = event.outputs
//        try {
//            val address = Wallet.getLastUnusedAddress()
//            val script = Address(address).scriptPubkey().toBytes().toUByteArray().toByteArray()
//            val txOut: Array<TxOut> = arrayOf()
//            val res = LDKKeysManager?.spend_spendable_outputs(
//                outputs,
//                txOut,
//                script,
//                1000,
//                Option_u32Z.None.none()
//            )
//
//            if (res != null) {
//                if (res.is_ok) {
//                    val tx = (res as Result_TransactionNoneZ.Result_TransactionNoneZ_OK).res
//                    val txs: Array<ByteArray> = arrayOf()
//                    txs.plus(tx)
//
//                    LDKBroadcaster.broadcast_transactions(txs)
//                }
//            }
//
//        } catch (e: Exception) {
//            Log.i(LDKTAG, "Error: ${e.message}")
//        }

    }

    if (event is Event.PaymentClaimable) {
        Log.i(LDKTAG, "Event.PaymentClaimable")
//        if (event.payment_hash != null) {
//            val purpose = event.purpose
//            val paymentPreimage = (purpose.payment_preimage as Option_ThirtyTwoBytesZ.Some).some
//
//            channelManager?.claim_funds(paymentPreimage)
//        }
    }

    if (event is Event.PaymentClaimed) {
        Log.i(LDKTAG, "Claimed Payment: ${event.payment_hash.toHex()}")
    }
}
package com.example.bitzo.domain.lightning

import com.example.bitzo.domain.bitcoin.Wallet
import org.ldk.structs.EcdsaChannelSigner
import org.ldk.structs.KeysManager
import org.ldk.structs.Result_CVec_u8ZNoneZ
import org.ldk.structs.Result_EcdsaChannelSignerDecodeErrorZ
import org.ldk.structs.Result_ShutdownScriptInvalidShutdownScriptZ
import org.ldk.structs.Result_ShutdownScriptNoneZ
import org.ldk.structs.ShutdownScript
import org.ldk.structs.SignerProvider
import org.ldk.structs.SpendableOutputDescriptor
import org.ldk.structs.WitnessProgram
import org.ldk.util.UInt128
import org.ldk.util.WitnessVersion

class LDKKeysManager(seed: ByteArray, startTimeSecs: Long, startTimeNano: Int, wallet: Wallet) {
    var inner: KeysManager
    var wallet: Wallet
    var signerProvider: LDKSignerProvider

    init {
        this.inner = KeysManager.of(seed, startTimeSecs, startTimeNano)
        this.wallet = wallet
        signerProvider = LDKSignerProvider()
        signerProvider.ldkkeysManager = this
    }

    // We drop all occurences of `SpendableOutputDescriptor::StaticOutput` (since they will be
    // spendable by the BDK wallet) and forward any other descriptors to
    // `KeysManager::spend_spendable_outputs`.
    //
    // Note you should set `locktime` to the current block height to mitigate fee sniping.
    // See https://bitcoinops.org/en/topics/fee-sniping/ for more information.
    fun spend_spendable_outputs(
        descriptors: Array<SpendableOutputDescriptor>,
        changeDestinationScript: ByteArray,
    ): Result_CVec_u8ZNoneZ? {
        val onlyNonStatic: Array<SpendableOutputDescriptor> =
            descriptors.filter { it !is SpendableOutputDescriptor.StaticOutput }.toTypedArray()

        return inner.sign_spendable_outputs_psbt(
            onlyNonStatic,
            changeDestinationScript
        )
    }
}

class LDKSignerProvider : SignerProvider.SignerProviderInterface {
    var ldkkeysManager: LDKKeysManager? = null

    override fun generate_channel_keys_id(p0: Boolean, p1: Long, p2: UInt128?): ByteArray {
        return ldkkeysManager!!.inner.as_SignerProvider().generate_channel_keys_id(p0, p1, p2)
    }

    override fun derive_channel_signer(p0: Long, p1: ByteArray?): EcdsaChannelSigner? {
        return ldkkeysManager!!.inner.as_SignerProvider().derive_channel_signer(p0, p1)
    }

    override fun read_chan_signer(p0: ByteArray?): Result_EcdsaChannelSignerDecodeErrorZ? {
        return ldkkeysManager!!.inner.as_SignerProvider().read_chan_signer(p0)
    }

    // We return the destination and shutdown scripts derived by the BDK wallet.
    @OptIn(ExperimentalUnsignedTypes::class)
    override fun get_destination_script(p0: ByteArray?): Result_CVec_u8ZNoneZ {
        val address = ldkkeysManager!!.wallet.getLastUnusedAddress().address
        return Result_CVec_u8ZNoneZ.ok(
            address.scriptPubkey().toBytes().toUByteArray().toByteArray()
        )
    }

    // Only applies to cooperative close transactions.
    override fun get_shutdown_scriptpubkey(): Result_ShutdownScriptNoneZ {
        val address = ldkkeysManager!!.wallet.getLastUnusedAddress().address

        return WitnessProgram(address.toString().toByteArray(), WitnessVersion("V0".toByte())).let {
            val result = ShutdownScript.new_witness_program(it)
            Result_ShutdownScriptNoneZ.ok(
                (result as Result_ShutdownScriptInvalidShutdownScriptZ.Result_ShutdownScriptInvalidShutdownScriptZ_OK).res
            )
        }

//        return when (val payload: Payload = address.payload()) {
//            is Payload.WitnessProgram -> {
//                val ver = when (payload.version.name) {
//                    in "V0".."V16" -> payload.version.name.substring(1).toIntOrNull() ?: 0
//                    else -> 0 // Default to 0 if it doesn't match any "V0" to "V16"
//                }
//
//                val result = ShutdownScript.new_witness_program(
//                    WitnessProgram(
//                        payload.program.toUByteArray().toByteArray(),
//                        WitnessVersion(ver.toByte())
//                    )
//                )
//                Result_ShutdownScriptNoneZ.ok((result as Result_ShutdownScriptInvalidShutdownScriptZ.Result_ShutdownScriptInvalidShutdownScriptZ_OK).res)
//            }
//
//            else -> {
//                Result_ShutdownScriptNoneZ.err()
//            }
//        }
//    }
    }
}
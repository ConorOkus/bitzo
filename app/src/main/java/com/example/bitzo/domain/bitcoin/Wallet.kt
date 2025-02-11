package com.example.bitzo.domain.bitcoin

import android.util.Log
import com.example.bitzo.domain.lightning.toHex
import com.example.bitzo.utils.RequiredInitialWalletData
import com.example.bitzo.utils.TxType
import com.example.bitzo.utils.WalletRepository
import com.example.bitzo.utils.netSendWithoutFees
import com.example.bitzo.utils.txType
import org.bitcoindevkit.Address
import org.bitcoindevkit.AddressInfo
import org.bitcoindevkit.Connection
import org.bitcoindevkit.DerivationPath
import org.bitcoindevkit.ChainPosition as BdkChainPosition
import org.rustbitcoin.bitcoin.Amount
import org.bitcoindevkit.Descriptor
import org.bitcoindevkit.DescriptorSecretKey
import org.bitcoindevkit.ElectrumClient
import org.bitcoindevkit.EsploraClient
import org.rustbitcoin.bitcoin.FeeRate
import org.bitcoindevkit.KeychainKind
import org.bitcoindevkit.Mnemonic
import org.rustbitcoin.bitcoin.Network
import org.bitcoindevkit.Psbt
import org.bitcoindevkit.Transaction
import org.bitcoindevkit.TxBuilder
import org.bitcoindevkit.Update
import org.bitcoindevkit.WordCount
import org.rustbitcoin.bitcoin.Script

private const val TAG = "WalletObject"
//private const val SIGNET_ELECTRUM_URL: String = "ssl://mempool.space:60602"
private const val SIGNET_ESPLORA_URL: String = "https://mutinynet.com/signet/api"
const val PERSISTENCE_VERSION = "V1"

object Wallet {
    private lateinit var wallet: org.bitcoindevkit.Wallet
    private lateinit var dbPath: String
    private lateinit var dbConnection: Connection

    private val blockchainClient: EsploraClient by lazy { EsploraClient(SIGNET_ESPLORA_URL) }
    private var fullScanRequired: Boolean = !WalletRepository.isFullScanCompleted()

    // Setting the path requires the application context and is done once by BitzoApplication
    fun setPathAndConnectDb(path: String) {
        dbPath = "$path/bitzoDB_$PERSISTENCE_VERSION.sqlite3"
        Log.i(TAG, "Loading directory path: $dbPath")
        dbConnection = Connection(dbPath)

    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun getLdkEntropy(): ByteArray {
        val mnemonic = WalletRepository.getMnemonic()
        val bip32RootKey = DescriptorSecretKey(
            network = Network.SIGNET,
            mnemonic = Mnemonic.fromString(mnemonic),
            password = null,
        )
        val derivationPath = DerivationPath("m/535h")
        val child = bip32RootKey.derive(derivationPath)
        val entropy = child.secretBytes().toUByteArray().toByteArray()

        return entropy
    }


    fun createWallet() {
        val mnemonic = Mnemonic(WordCount.WORDS12)
        val bip32ExtendedRootKey = DescriptorSecretKey(Network.SIGNET, mnemonic, null)
        val descriptor: Descriptor =
            Descriptor.newBip84(bip32ExtendedRootKey, KeychainKind.EXTERNAL, Network.SIGNET)
        val changeDescriptor: Descriptor =
            Descriptor.newBip84(bip32ExtendedRootKey, KeychainKind.INTERNAL, Network.SIGNET)
        initialize(
            descriptor = descriptor,
            changeDescriptor = changeDescriptor,
        )
        WalletRepository.saveWallet(
            dbPath,
            descriptor.toStringWithSecret(),
            changeDescriptor.toStringWithSecret()
        )
        WalletRepository.saveMnemonic(mnemonic.toString())
    }

    private fun initialize(
        descriptor: Descriptor,
        changeDescriptor: Descriptor,
    ) {
        wallet = org.bitcoindevkit.Wallet(
            descriptor,
            changeDescriptor,
            Network.SIGNET,
            dbConnection
        )
    }

    fun loadWallet() {
        val initialWalletData: RequiredInitialWalletData = WalletRepository.getInitialWalletData()
        Log.i(TAG, "Loading existing wallet with descriptor: ${initialWalletData.descriptor}")
        Log.i(TAG, "Loading existing wallet with change descriptor: ${initialWalletData.changeDescriptor}")
        val descriptor = Descriptor(initialWalletData.descriptor, Network.SIGNET)
        val changeDescriptor = Descriptor(initialWalletData.changeDescriptor, Network.SIGNET)

        wallet = org.bitcoindevkit.Wallet.load(
            descriptor,
            changeDescriptor,
            dbConnection,
        )
    }

    fun recoverWallet(recoveryPhrase: String) {
        val mnemonic = Mnemonic.fromString(recoveryPhrase)
        val bip32ExtendedRootKey = DescriptorSecretKey(Network.SIGNET, mnemonic, null)
        val descriptor: Descriptor =
            Descriptor.newBip84(bip32ExtendedRootKey, KeychainKind.EXTERNAL, Network.SIGNET)
        val changeDescriptor: Descriptor =
            Descriptor.newBip84(bip32ExtendedRootKey, KeychainKind.INTERNAL, Network.SIGNET)
        initialize(
            descriptor = descriptor,
            changeDescriptor = changeDescriptor,
        )
        WalletRepository.saveWallet(
            dbPath,
            descriptor.toStringWithSecret(),
            changeDescriptor.toStringWithSecret()
        )
        WalletRepository.saveMnemonic(mnemonic.toString())
    }

    private fun fullScan() {
        val fullScanRequest = wallet.startFullScan().build()
        val update: Update = blockchainClient.fullScan(
            request = fullScanRequest,
            stopGap = 20u,
            parallelRequests = 10u,
        )
        wallet.applyUpdate(update)
        wallet.persist(dbConnection)
    }

    fun sync() {
        if (fullScanRequired) {
            Log.i(TAG, "Full scan required")
            fullScan()
            WalletRepository.fullScanCompleted()
            fullScanRequired = false
        } else {
            Log.i(TAG, "Just a normal sync!")
            val syncRequest = wallet.startSyncWithRevealedSpks().build()
            val update = blockchainClient.sync(
                request = syncRequest,
                parallelRequests = 10u,
            )
            wallet.applyUpdate(update)
            wallet.persist(dbConnection)
        }
    }

    fun getBalance(): ULong {
        return wallet.balance().total.toSat()
    }

    fun getLastUnusedAddress(): AddressInfo {
        return wallet.revealNextAddress(KeychainKind.EXTERNAL)
    }

    fun createPsbt(recipientAddress: String, amount: Amount, feeRate: FeeRate): Psbt {
        val recipientScriptPubKey = Address(recipientAddress, Network.SIGNET).scriptPubkey()
        return TxBuilder()
            .addRecipient(recipientScriptPubKey, amount)
            .feeRate(feeRate)
            .finish(wallet)
    }

    fun sign(psbt: Psbt) {
        wallet.sign(psbt)
    }

    fun listTransactions(): List<TransactionDetails> {
        val transactions = wallet.transactions()
        return transactions.map { tx ->
            val (sent, received) = wallet.sentAndReceived(tx.transaction)
            val fee = wallet.calculateFee(tx.transaction)
            val feeRate = wallet.calculateFeeRate(tx.transaction)
            val txType: TxType = txType(sent = sent.toSat(), received = received.toSat())
            val paymentAmount = if (txType == TxType.PAYMENT) {
                netSendWithoutFees(
                    txSatsOut = sent.toSat(),
                    txSatsIn = received.toSat(),
                    fee = fee.toSat()
                )
            } else {
                0uL
            }
            val chainPosition: ChainPosition = when (val position = tx.chainPosition) {
                is BdkChainPosition.Unconfirmed -> ChainPosition.Unconfirmed
                is BdkChainPosition.Confirmed -> ChainPosition.Confirmed(
                    position.confirmationBlockTime.blockId.height,
                    position.confirmationBlockTime.confirmationTime
                )
            }

            TransactionDetails(
                txid = tx.transaction.computeTxid(),
                sent = sent,
                received = received,
                paymentAmount = paymentAmount,
                fee = fee,
                feeRate = feeRate,
                txType = txType,
                chainPosition = chainPosition
            )
        }
    }

    fun getTransaction(txid: String): TransactionDetails? {
        val allTransactions = listTransactions()
        allTransactions.forEach {
            if (it.txid == txid) {
                return it
            }
        }
        return null
    }

    fun broadcast(tx: Transaction): String {
        blockchainClient.broadcast(tx)
        return tx.computeTxid()
    }

    fun getBlockTipHeight(): Int {
        return blockchainClient.getHeight().toInt()
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun buildFundingTx(value: Long, script: ByteArray): Transaction {
        sync()
        val scriptListUByte: List<UByte> = script.toUByteArray().asList()
        val outputScript = Script(scriptListUByte)
        val psbt = TxBuilder()
            .addRecipient(outputScript, Amount.fromSat(value.toULong()))
            .feeRate(FeeRate.fromSatPerVb(4u))
            .finish(wallet)
        val rawTx = psbt.extractTx().serialize().toUByteArray().toByteArray()
        Log.i(TAG, "The raw funding tx is ${rawTx.toHex()}")
        return psbt.extractTx()
    }
}

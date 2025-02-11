package com.example.bitzo.domain.lightning

import android.content.Context
import android.util.Log
import com.example.bitzo.LDKEventHandler
import com.example.bitzo.data.remote.Service
import com.example.bitzo.domain.bitcoin.Wallet
import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.io.BaseEncoding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ldk.batteries.ChannelManagerConstructor
import org.ldk.batteries.NioPeerHandler
import org.ldk.enums.ChannelMonitorUpdateStatus
import org.ldk.enums.Network
import org.ldk.structs.BroadcasterInterface
import org.ldk.structs.ChainMonitor
import org.ldk.structs.ChannelHandshakeConfig
import org.ldk.structs.ChannelHandshakeLimits
import org.ldk.structs.ChannelManager
import org.ldk.structs.ChannelMonitor
import org.ldk.structs.ChannelMonitorUpdate
import org.ldk.structs.FeeEstimator
import org.ldk.structs.Filter
import org.ldk.structs.Logger
import org.ldk.structs.MultiThreadedLockableScore
import org.ldk.structs.NetworkGraph
import org.ldk.structs.Option_FilterZ
import org.ldk.structs.Option_u32Z
import org.ldk.structs.Option_u64Z
import org.ldk.structs.OutPoint
import org.ldk.structs.PeerManager
import org.ldk.structs.Persist
import org.ldk.structs.ProbabilisticScorer
import org.ldk.structs.ProbabilisticScoringDecayParameters
import org.ldk.structs.ProbabilisticScoringFeeParameters
import org.ldk.structs.RapidGossipSync
import org.ldk.structs.Record
import org.ldk.structs.Result_NetworkGraphDecodeErrorZ
import org.ldk.structs.Result_ProbabilisticScorerDecodeErrorZ
import org.ldk.structs.SignerProvider
import org.ldk.structs.UserConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectOutputStream
import java.net.InetSocketAddress

const val LDKTAG = "NODE"

object Node {
    private lateinit var filesPath: String

    private val feeEstimator: FeeEstimator = FeeEstimator.new_impl(LDKFeeEstimator)
    private val txBroadcaster: BroadcasterInterface = BroadcasterInterface.new_impl(LDKBroadcaster)
    private val txFilter: Filter = Filter.new_impl(LDKTxFilter)
    private val filter = Option_FilterZ.some(txFilter)
    private lateinit var networkGraph: NetworkGraph
    private lateinit var channelManagerConstructor: ChannelManagerConstructor
    private lateinit var nioPeerHandler: NioPeerHandler
    private lateinit var peerManager: PeerManager
    private lateinit var scorer: MultiThreadedLockableScore

    var channelManager: ChannelManager? = null



    suspend fun start(context: Context) {
        filesPath = context.filesDir.toString()

        val networkGraphFile = File(filesPath, "/network-graph.bin")
        if (networkGraphFile.exists()) {
            (NetworkGraph.read(networkGraphFile.readBytes(), logger) as? Result_NetworkGraphDecodeErrorZ.Result_NetworkGraphDecodeErrorZ_OK)?.let { res ->
                networkGraph = res.res
                Log.i(LDKTAG, "Network graph loaded from disk.")
            }
        } else {
            val file = File(filesPath, "/network-graph.bin")
            withContext(Dispatchers.IO) {
                file.createNewFile()
            }
            networkGraph = NetworkGraph.of(Network.LDKNetwork_Signet, logger)
        }

        val rgs = RapidGossipSync.of(networkGraph, logger)
        val lastSync = (networkGraph._last_rapid_gossip_sync_timestamp as Option_u32Z.Some).some
        val snapshot = Service.create().getSnapshot(lastSync)
        val timestampSeconds = Option_u64Z.some(System.currentTimeMillis() / 1000)
        val res = rgs.update_network_graph_no_std(snapshot, timestampSeconds)
        if (res.is_ok) {
            Log.i(LDKTAG, "RGS Updated")
        }

        var serializedChannelManager: ByteArray? = null
        var serializedChannelMonitors = arrayOf<ByteArray>()

        val channelManagerFile = File(filesPath, "/channel-manager.bin")
        if(channelManagerFile.exists()) {
            serializedChannelManager = channelManagerFile.absoluteFile.readBytes()
        }

        // Read Channel Monitor state from disk
        // Initialize the hashmap where we'll store the `ChannelMonitor`s read from disk.
        // This hashmap will later be given to the `ChannelManager` on initialization.
        val channelMonitorDirectory = File(filesPath,"/channels/")
        if (channelMonitorDirectory.isDirectory) {
            val files: Array<String> = channelMonitorDirectory.list()
            if (files.isNotEmpty()) {
                val channelMonitorList = serializedChannelMonitors.toMutableList()
                files.forEach {
                    channelMonitorList.add(File("${channelMonitorDirectory}/${it}").readBytes())
                }

                serializedChannelMonitors = channelMonitorList.toTypedArray()

            }
        } else {
            channelMonitorDirectory.mkdir()
            Log.i(LDKTAG, "Creating directory at $channelMonitorDirectory")
        }

        val scorerFile = File("${filesPath}/scorer.bin")
        if(scorerFile.exists()) {
            val scorerReaderResult = ProbabilisticScorer.read(scorerFile.readBytes(), ProbabilisticScoringDecayParameters.with_default(), networkGraph, logger)
            if (scorerReaderResult.is_ok) {
                val probabilisticScorer =
                    (scorerReaderResult as Result_ProbabilisticScorerDecodeErrorZ.Result_ProbabilisticScorerDecodeErrorZ_OK).res
                scorer = MultiThreadedLockableScore.of(probabilisticScorer.as_Score())
                Log.i(LDKTAG, "LDK: Probabilistic Scorer loaded and running")
            } else {
                Log.i(LDKTAG, "LDK: Couldn't load Probabilistic Scorer")
                val decayParams = ProbabilisticScoringDecayParameters.with_default()
                val probabilisticScorer = ProbabilisticScorer.of(decayParams, networkGraph, logger)
                scorer = MultiThreadedLockableScore.of(probabilisticScorer.as_Score())
                Log.i(LDKTAG, "LDK: Creating new Probabilistic Scorer")
            }
        } else {
            val decayParams = ProbabilisticScoringDecayParameters.with_default()
            val probabilisticScorer = ProbabilisticScorer.of(decayParams, networkGraph, logger)
            scorer = MultiThreadedLockableScore.of(probabilisticScorer.as_Score())
        }

        val blockTipHash = Service.create().getBlockTipHash()
        val blockTipHeight = Wallet.getBlockTipHeight()

        try {
            if (serializedChannelManager != null && serializedChannelManager.isNotEmpty()) {
                // loading from disk (restarting)
                channelManagerConstructor = ChannelManagerConstructor(
                    serializedChannelManager,
                    serializedChannelMonitors,
                    userConfig,
                    LDKKeysManager.inner.as_EntropySource(),
                    LDKKeysManager.inner.as_NodeSigner(),
                    SignerProvider.new_impl(LDKKeysManager.signerProvider),
                    feeEstimator,
                    chainMonitor,
                    txFilter,
                    networkGraph.write(),
                    ProbabilisticScoringDecayParameters.with_default(),
                    ProbabilisticScoringFeeParameters.with_default(),
                    scorer.write(),
                    null,
                    txBroadcaster,
                    logger
                )

                channelManager = channelManagerConstructor.channel_manager
                nioPeerHandler = channelManagerConstructor.nio_peer_handler
                peerManager = channelManagerConstructor.peer_manager
                networkGraph = channelManagerConstructor.net_graph

                channelManagerConstructor.chain_sync_completed(
                    LDKEventHandler,
                    true
                )

                // If you want to communicate from your computer to your emulator,
                // the IP address to use is 127.0.0.1 and you need to do some port forwarding
                // using ADB in command line e.g adb forward tcp:9777 tcp:9777
                // If you want to do the reverse use 10.0.2.2 instead of localhost

                channelManagerConstructor.nio_peer_handler.bind_listener(InetSocketAddress("127.0.0.1", 9777))

            } else {
                // fresh start
                channelManagerConstructor = ChannelManagerConstructor(
                    Network.LDKNetwork_Signet,
                    userConfig,
                    blockTipHash.toByteArray(),
                    blockTipHeight,
                    LDKKeysManager.inner.as_EntropySource(),
                    LDKKeysManager.inner.as_NodeSigner(),
                    SignerProvider.new_impl(LDKKeysManager.signerProvider),
                    feeEstimator,
                    chainMonitor,
                    networkGraph,
                    ProbabilisticScoringDecayParameters.with_default(),
                    ProbabilisticScoringFeeParameters.with_default(),
                    null,
                    txBroadcaster,
                    logger
                )

                channelManager = channelManagerConstructor.channel_manager
                peerManager = channelManagerConstructor.peer_manager
                nioPeerHandler = channelManagerConstructor.nio_peer_handler
                networkGraph = channelManagerConstructor.net_graph
                channelManagerConstructor.chain_sync_completed(
                    LDKEventHandler,
                    true
                )

                channelManagerConstructor.nio_peer_handler.bind_listener(InetSocketAddress("127.0.0.1", 9777))
            }
        } catch (e: Exception) {
            Log.i(LDKTAG, "LDK: can't start, ${e.message}")
        }

    }


    private val logger: Logger = Logger.new_impl {
        fun log(record: Record?) {
            val rawLog = record?._args.toString()
            val file = File(filesPath, "log.txt")

            try {
                if (!file.exists()) {
                    file.createNewFile()

                    file.appendText(rawLog + "\n")
                } else {
                    file.appendText(rawLog + "\n")
                }
            } catch (e: Exception) {
                Log.i("LOGGER", "Failed to create log file: ${e.message}")
            }
        }
    }

    private val LDKPersister = object : Persist.PersistInterface {
        private fun persist(id: OutPoint?, data: ByteArray?) {
            if(id != null && data != null) {
                val identifier = "${filesPath}/channels/${id._txid.toHex()}.bin"
                write(identifier, data)
            }
        }

        override fun persist_new_channel(
            id: OutPoint?,
            data: ChannelMonitor?
        ): ChannelMonitorUpdateStatus? {
            return try {
                if (data != null && id != null) {
                    Log.i("PERSIST", "persist_new_channel: ${id._txid.toHex()}")
                    persist(id, data.write())
                }
                ChannelMonitorUpdateStatus.LDKChannelMonitorUpdateStatus_Completed
            } catch (e: Exception) {
                Log.i("PERSIST", "Failed to write to file: ${e.message}")
                ChannelMonitorUpdateStatus.LDKChannelMonitorUpdateStatus_UnrecoverableError
            }
        }

        // Consider returning ChannelMonitorUpdateStatus::InProgress for async backups
        override fun update_persisted_channel(
            id: OutPoint?,
            update: ChannelMonitorUpdate?,
            data: ChannelMonitor?
        ): ChannelMonitorUpdateStatus? {
            return try {
                if (data != null && id != null) {
                    Log.i("PERSIST", "update_persisted_channel: ${id._txid.toHex()}")
                    persist(id, data.write())
                }
                ChannelMonitorUpdateStatus.LDKChannelMonitorUpdateStatus_Completed
            } catch (e: Exception) {
                Log.i("PERSIST", "Failed to write to file: ${e.message}")
                ChannelMonitorUpdateStatus.LDKChannelMonitorUpdateStatus_UnrecoverableError

            }

        }

        override fun archive_persisted_channel(p0: OutPoint?) {
            TODO("Not yet implemented")
        }
    }

    private val persister: Persist = Persist.new_impl(LDKPersister)
    val chainMonitor: ChainMonitor = ChainMonitor.of(filter, txBroadcaster, logger, feeEstimator, persister)

    // Providing keys for signing lightning transactions
    private val LDKKeysManager = LDKKeysManager(Wallet.getLdkEntropy(), System.currentTimeMillis() / 1000, (System.currentTimeMillis() * 1000).toInt(), Wallet)

    private val channelHandshakeConfig = ChannelHandshakeConfig.with_default().apply {
        _minimum_depth = 1
        _announce_for_forwarding = false
    }

    private val channelHandshakeLimits = ChannelHandshakeLimits.with_default().apply {
        _max_minimum_depth = 1
    }
    private val userConfig = UserConfig.with_default().apply {
        _channel_handshake_config = channelHandshakeConfig
        _channel_handshake_limits = channelHandshakeLimits
        _accept_inbound_channels = true
    }

}

// Helper functions
fun ByteArray.toHex(): String {
    return BaseEncoding.base16().encode(this).lowercase()
}

fun String.toByteArray(): ByteArray {
    return BaseEncoding.base16().decode(this.uppercase())
}

fun convertToByteArray(obj: Any): ByteArray {
    val bos = ByteArrayOutputStream()
    val oos = ObjectOutputStream(bos)
    oos.writeObject(obj)
    oos.flush()
    return bos.toByteArray()
}

fun storeEvent(eventsPath: String, params: WritableMap) {
    val directory = File(eventsPath)
    if (!directory.exists()) {
        directory.mkdir()
    }

    File(eventsPath + "/" + System.currentTimeMillis() + ".json").writeText(params.toString())
}

fun write(identifier: String, data: ByteArray?) {
    val fileName = identifier
    val file = File(fileName)
    if(data != null) {
        Log.i("WRITE", "Writing to file: $fileName")
        file.writeBytes(data)
    }
}

class WritableMap {
    var json: String = ""
    var first = true

//    fun putNull(@NonNull var1: String?)
//    fun putBoolean(@NonNull var1: String?, var2: Boolean)
//    fun putDouble(@NonNull var1: String?, var2: Double)
//    fun putInt(@NonNull var1: String?, var2: Int)

    fun putString(var1: String, var2: String?) {
        if (!first) json += ','
        json += "\"$var1\":\"$var2\""
        first = false
    }
//    fun putArray(@NonNull var1: String?, @Nullable var2: ReadableArray?)
//    fun putMap(@NonNull var1: String?, @Nullable var2: ReadableMap?)
//    fun merge(@NonNull var1: ReadableMap?)
//    fun copy(): WritableMap?

    override fun toString(): String {
        return "{$json}"
    }
}
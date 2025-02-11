package com.example.bitzo

import android.app.Application
import android.content.Context
import android.util.Log
import com.example.bitzo.domain.bitcoin.Wallet
import com.example.bitzo.utils.WalletRepository

class BitzoApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        val filesDirectoryPath: String = applicationContext.filesDir.toString()

        // initialize repositories and set shared preferences
        WalletRepository.setSharedPreferences(applicationContext.getSharedPreferences("wallet", Context.MODE_PRIVATE))

        // initialize Wallet object with path variable
        Wallet.setPathAndConnectDb(filesDirectoryPath)

        // Initialize the LDK data directory if necessary.
//        val directory = File(filesDirectoryPath, "/lightning")
//        if(!directory.exists()) {
//            directory.mkdirs()
//        }

        // Start LDK Node
        // Launch a coroutine in the Application scope!
//        CoroutineScope(Dispatchers.IO).launch {
//            // Start LDK Node within the coroutine
//            Node.start(applicationContext)
//        }
    }
}
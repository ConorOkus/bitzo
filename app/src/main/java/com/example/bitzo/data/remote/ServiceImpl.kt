package com.example.bitzo.data.remote

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*


class ServiceImpl(private val client: HttpClient) : Service {
    override suspend fun getBlockTipHash(): String {
        val httpResponse: HttpResponse = client.get("https://mutinynet.com/api/blocks/tip/hash")
        return httpResponse.body()
    }

    override suspend fun getBlockTipHeight(): String {
        val httpResponse: HttpResponse = client.get("https://mutinynet.com/api/blocks/tip/height")
        return httpResponse.body()
    }

    override suspend fun getSnapshot(lastSyncTimeStamp: Int): ByteArray {
        val httpResponse: HttpResponse = client.get("https://rgs.mutinynet.com/snapshot/${lastSyncTimeStamp}")
        return httpResponse.body()
    }

}
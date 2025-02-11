package com.example.bitzo.data.remote

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


interface Service {

    suspend fun getBlockTipHash() : String

    suspend fun getBlockTipHeight() : String

    suspend fun getSnapshot(lastSyncTimeStamp: Int) : ByteArray

    companion object {
        fun create() : Service {
            return ServiceImpl(
                client = HttpClient {
                    install(Logging) {
                        logger = Logger.DEFAULT
                        level = LogLevel.HEADERS
                    }
                    install(ContentNegotiation) {
                        json(
                            json = Json {
                                prettyPrint = true
                                isLenient = true
                                ignoreUnknownKeys = true
                            }
                        )
                    }
                }
            )
        }
    }
}
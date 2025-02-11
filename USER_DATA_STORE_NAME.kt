package com.example.bitzo.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.bitzo.model.UserDetails
import kotlinx.coroutines.flow.map

const val USER_DATA_STORE_NAME = "user_data"

val Context.preferenceDatastore: DataStore<Preferences> by preferencesDataStore(name = USER_DATA_STORE_NAME)

class DataStoreManager(val context: Context) {

    companion object {

        val descriptor = stringPreferencesKey("DESCRIPTOR")
        val changeDescriptor = stringPreferencesKey("CHANGE_DESCRIPTOR")
        val path = stringPreferencesKey("PATH")
        val mnemonic = stringPreferencesKey("MNEMONIC")
        val fullScanCompleted = booleanPreferencesKey("FULL_SCAN_COMPLETED")

    }

    suspend fun saveToDateStore(userDetails: UserDetails) {
        context.preferenceDatastore.edit {
            it[descriptor] = userDetails.descriptor
            it[changeDescriptor] = userDetails.changeDescriptor
            it[path] = userDetails.path
            it[mnemonic] = userDetails.mnemonic
            it[fullScanCompleted] = userDetails.fullScanCompleted
        }
    }

    fun getFromDataStore() = context.preferenceDatastore.data.map {
        UserDetails(
            descriptor = it[descriptor] ?: "",
            changeDescriptor = it[changeDescriptor] ?: "",
            path = it[path] ?: "",
            mnemonic = it[mnemonic] ?: "",
            fullScanCompleted = it[fullScanCompleted] ?: false
        )
    }

    suspend fun clearDataStore() {
        context.preferenceDatastore.edit {
            it.clear()
        }
    }

}
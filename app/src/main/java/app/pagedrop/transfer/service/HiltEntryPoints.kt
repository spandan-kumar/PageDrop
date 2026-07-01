package app.pagedrop.transfer.service

import app.pagedrop.data.KindleSettings
import app.pagedrop.data.local.database.AppDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun appDatabase(): AppDatabase
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun kindleSettings(): KindleSettings
}

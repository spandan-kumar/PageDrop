package app.pagedrop.transfer.di

import app.pagedrop.transfer.hotspot.HotspotHelper
import app.pagedrop.transfer.server.BookServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing transfer-layer singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object TransferModule {

    @Provides
    @Singleton
    fun provideBookServer(): BookServer {
        return BookServer()
    }

    @Provides
    @Singleton
    fun provideHotspotHelper(): HotspotHelper {
        return HotspotHelper()
    }
}

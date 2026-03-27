package com.airmesh.di

import android.content.Context
import androidx.room.Room
import com.airmesh.data.db.AppDatabase
import com.airmesh.data.db.DeviceStatsDao
import com.airmesh.data.db.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "airmesh.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMessageDao(db: AppDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideDeviceStatsDao(db: AppDatabase): DeviceStatsDao = db.deviceStatsDao()
}

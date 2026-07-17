package com.example

import android.app.Application
import androidx.room.Room
import com.example.data.AppDatabase
import com.example.data.AppRepository

class KgiApplication : Application() {
    lateinit var database: AppDatabase
        private set

    lateinit var repository: AppRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "kgi_diesels_database"
        )
        .fallbackToDestructiveMigration()
        .build()

        repository = AppRepository(
            userDao = database.userDao(),
            loadDao = database.loadDao(),
            chatDao = database.chatDao(),
            commissionDao = database.commissionDao(),
            jobDao = database.jobDao()
        )
    }
}

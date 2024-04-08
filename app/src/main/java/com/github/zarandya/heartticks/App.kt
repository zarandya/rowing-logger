package com.github.zarandya.heartticks

import android.app.Application
import androidx.room.Room
import com.github.zarandya.heartticks.db.AppDatabase

class App: Application() {

    override fun onCreate() {
        super.onCreate()

        DB = Room
            .databaseBuilder(applicationContext, AppDatabase::class.java, "batnet-database")
            .fallbackToDestructiveMigration()
            .build()

    }

    companion object {
        @JvmStatic
        lateinit var DB: AppDatabase private set
    }
}
package com.example

import android.app.Application
import com.example.audio.AudioController
import com.example.data.repository.MusicRepository

class AlreadyApp : Application() {
    lateinit var repository: MusicRepository
        private set
    lateinit var audioController: AudioController
        private set

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(this)
        audioController = AudioController.getInstance(this)
    }
}

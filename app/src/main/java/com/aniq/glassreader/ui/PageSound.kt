package com.aniq.glassreader.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import com.aniq.glassreader.R
import com.aniq.glassreader.data.Store

/**
 * The soft paper swish played when a page turns. Loaded once at app start; plays only when
 * "Page turn sound" is on in Settings and the phone isn't on silent or vibrate.
 */
object PageSound {
    private var pool: SoundPool? = null
    private var id = 0
    private var ready = false
    private var audio: AudioManager? = null

    fun init(context: Context) {
        if (pool != null) return
        audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        pool = SoundPool.Builder()
            .setMaxStreams(2)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build().also { p ->
                p.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
                id = p.load(context.applicationContext, R.raw.page_flip, 1)
            }
    }

    fun play() {
        if (!Store.settings.value.pageSound || !ready) return
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        val rate = 0.94f + Math.random().toFloat() * 0.12f   // tiny pitch variation so turns don't sound identical
        val vol = 0.55f
        pool?.play(id, vol, vol, 1, 0, rate)
    }
}

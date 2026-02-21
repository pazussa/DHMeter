package com.dhmeter.data.repository

import android.content.Context
import com.dhmeter.domain.repository.TrackAutoStartRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrackAutoStartRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : TrackAutoStartRepository {

    companion object {
        private const val PREFS_NAME = "dhmeter_track_autostart"
        private const val KEY_PREFIX = "track_autostart_"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isAutoStartEnabled(trackId: String): Boolean {
        val key = buildKey(trackId)
        return prefs.getBoolean(key, false)
    }

    override fun setAutoStartEnabled(trackId: String, enabled: Boolean) {
        val key = buildKey(trackId)
        prefs.edit()
            .putBoolean(key, enabled)
            .apply()
    }

    override fun getEnabledTrackIds(): List<String> {
        return prefs.all.entries
            .asSequence()
            .filter { (key, value) ->
                key.startsWith(KEY_PREFIX) && (value as? Boolean == true)
            }
            .map { (key, _) -> key.removePrefix(KEY_PREFIX) }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun buildKey(trackId: String): String = "$KEY_PREFIX$trackId"
}

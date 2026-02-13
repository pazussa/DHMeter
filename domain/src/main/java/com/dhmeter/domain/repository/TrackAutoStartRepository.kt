package com.dhmeter.domain.repository

interface TrackAutoStartRepository {
    fun isAutoStartEnabled(trackId: String): Boolean
    fun setAutoStartEnabled(trackId: String, enabled: Boolean)
}

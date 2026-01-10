package com.dhmeter.domain.model

data class Track(
    val id: String,
    val name: String,
    val createdAt: Long,
    val locationHint: String? = null,
    val notes: String? = null
)

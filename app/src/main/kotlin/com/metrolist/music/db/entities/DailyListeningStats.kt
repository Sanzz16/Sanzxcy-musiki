package com.metrolist.music.db.entities

data class DailyListeningStats(
    val day: String,
    val playTime: Long,
    val plays: Int,
)

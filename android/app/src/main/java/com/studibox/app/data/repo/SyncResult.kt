package com.studibox.app.data.repo

sealed interface SyncResult {
    data class Success(val groups: Int, val lessons: Int, val version: String) : SyncResult
    data object UpToDate : SyncResult
    data class Failed(val reason: String, val offline: Boolean) : SyncResult
}

package com.example.pkmapp.core.database

import android.content.Context
import com.example.pkmapp.core.datastore.AppPreferences
import java.util.UUID

/**
 * Defines local multi-account isolation constants and sync states for Room v2+.
 */
object SyncStatus {
    const val SYNCED: Int = 0
    const val PENDING_PUSH: Int = 1
}

object AccountScope {
    const val GUEST_OWNER_ID: String = "local_guest"

    @JvmStatic
    fun currentOwnerId(context: Context): String {
        val id = AppPreferences.getInstance(context).current.activeOwnerId.trim()
        return id.ifEmpty { GUEST_OWNER_ID }
    }

    @JvmStatic
    fun newRequestId(): String = UUID.randomUUID().toString()
}

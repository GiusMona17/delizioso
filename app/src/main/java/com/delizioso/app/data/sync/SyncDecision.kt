package com.delizioso.app.data.sync

enum class SyncAction { NOTHING, PUSH, PULL, CONFLICT }

/**
 * What one sync run should do (spec §2, decision table).
 *
 * [serverEtag] is null when the Pi has nothing for this app; [baseEtag] is the version this
 * device last pushed or pulled; [localChanged] means the local data differs from that version.
 * Data that changed on both sides is never overwritten silently: that is a CONFLICT.
 */
fun decide(serverEtag: String?, baseEtag: String?, localChanged: Boolean, localEmpty: Boolean): SyncAction =
    when {
        serverEtag == null -> if (localEmpty) SyncAction.NOTHING else SyncAction.PUSH
        serverEtag == baseEtag -> if (localChanged) SyncAction.PUSH else SyncAction.NOTHING
        localEmpty -> SyncAction.PULL
        baseEtag != null && !localChanged -> SyncAction.PULL
        else -> SyncAction.CONFLICT
    }

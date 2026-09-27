package com.delizioso.app.data.sync

import org.json.JSONArray
import org.json.JSONObject

internal fun JSONArray.toStringList(): List<String> = (0 until length()).map { getString(it) }

internal fun JSONObject.toStringMap(): Map<String, String> =
    keys().asSequence().associateWith { getString(it) }

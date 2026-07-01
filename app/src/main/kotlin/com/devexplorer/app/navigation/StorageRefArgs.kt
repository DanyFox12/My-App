package com.devexplorer.app.navigation

import android.util.Base64
import com.devexplorer.core.model.StorageRef
import kotlinx.serialization.json.Json

/**
 * Encodes a [StorageRef] for use as a navigation argument.
 *
 * A SAF [StorageRef.raw] can contain a control-character separator and other
 * bytes that don't belong in a route string. We JSON-serialize the whole ref and
 * URL-safe Base64 it, so navigation carries an opaque, route-safe token that
 * round-trips back to the exact same ref in the destination.
 */
object StorageRefArgs {

    private val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun encode(ref: StorageRef): String {
        val json = Json.encodeToString(StorageRef.serializer(), ref)
        return Base64.encodeToString(json.toByteArray(Charsets.UTF_8), flags)
    }

    fun decode(arg: String): StorageRef {
        val json = String(Base64.decode(arg, flags), Charsets.UTF_8)
        return Json.decodeFromString(StorageRef.serializer(), json)
    }
}

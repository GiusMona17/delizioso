package com.delizioso.app.data.local

/** The three answers to "which palette?" — follow the phone, or force one. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        /** Unknown or missing values fall back to following the phone. */
        fun from(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

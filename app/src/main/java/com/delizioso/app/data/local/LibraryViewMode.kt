package com.delizioso.app.data.local

/**
 * How the library lists recipes.
 *
 * [CARDS] is the original hero layout — one big photo per recipe, which is
 * lovely at a dozen recipes and a lot of scrolling at fifty. [GRID] and [LIST]
 * trade image size for how many you can see at once.
 */
enum class LibraryViewMode(val key: String) {
    CARDS("cards"),
    GRID("grid"),
    LIST("list");

    companion object {
        fun from(key: String?): LibraryViewMode = entries.firstOrNull { it.key == key } ?: CARDS
    }
}

/**
 * The order recipes are listed in.
 *
 * [RECENT] is what the library always did — most recently touched first, which is
 * right for a handful of recipes and useless once you are hunting for a name.
 */
enum class LibrarySort(val key: String) {
    RECENT("recent"),
    NAME("name"),
    TIME("time");

    companion object {
        fun from(key: String?): LibrarySort = entries.firstOrNull { it.key == key } ?: RECENT
    }
}

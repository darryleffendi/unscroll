package com.darryl.unscroll.core

/** One switch in the settings screen. Keys are persisted; don't rename them. */
enum class Toggle(val key: String, val defaultOn: Boolean, val impliedBy: String? = null) {
    IG_HIDE_FEED("ig_hide_feed", true, impliedBy = "ig_hide_home"),
    IG_HIDE_HOME("ig_hide_home", false),
    IG_HIDE_EXPLORE("ig_hide_explore", true),
    IG_BLOCK_REELS_TAB("ig_block_reels_tab", true, impliedBy = "ig_block_reels_all"),
    IG_BLOCK_REELS_ALL("ig_block_reels_all", false),
    IG_OPEN_MESSAGES("ig_open_messages", false),
    YT_HIDE_HOME("yt_hide_home", true),
    YT_BLOCK_SHORTS_TAB("yt_block_shorts_tab", true, impliedBy = "yt_block_shorts_all"),
    YT_BLOCK_SHORTS_ALL("yt_block_shorts_all", false),
    YT_HIDE_UP_NEXT("yt_hide_up_next", true),
    TT_HIDE_FEEDS("tt_hide_feeds", true),
    X_HIDE_FOR_YOU("x_hide_for_you", true),
    LI_HIDE_FEED("li_hide_feed", true);

    companion object {
        fun byKey(key: String) = entries.firstOrNull { it.key == key }
    }
}

/** Immutable snapshot of the switches. A switch implied by another (greyed out in the UI) reads as on. */
data class Settings(private val values: Map<Toggle, Boolean> = emptyMap()) {
    fun stored(toggle: Toggle): Boolean = values[toggle] ?: toggle.defaultOn

    fun isImplied(toggle: Toggle): Boolean = toggle.impliedBy?.let { Toggle.byKey(it) }?.let { isOn(it) } ?: false

    fun isOn(toggle: Toggle): Boolean = stored(toggle) || isImplied(toggle)

    fun with(toggle: Toggle, on: Boolean) = Settings(values + (toggle to on))

    companion object {
        val DEFAULT = Settings()
        /** Everything on: handy in tests. */
        val ALL_ON = Settings(Toggle.entries.associateWith { true })
    }
}

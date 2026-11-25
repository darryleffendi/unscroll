package com.darryl.unscroll.apps

import com.darryl.unscroll.core.AppRules

object RulesRegistry {
    fun create(): Map<String, AppRules> {
        val tiktok = TikTokRules()
        return mapOf(
            "com.instagram.android" to InstagramRules(),
            "com.google.android.youtube" to YouTubeRules(),
            "com.zhiliaoapp.musically" to tiktok,
            "com.ss.android.ugc.trill" to tiktok,
            "com.twitter.android" to XRules(),
            "com.linkedin.android" to LinkedInRules(),
        )
    }
}

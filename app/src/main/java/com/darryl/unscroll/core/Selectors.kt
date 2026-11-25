package com.darryl.unscroll.core

import org.json.JSONObject

/**
 * How to find one UI element: resource ids first, then content descriptions (or, for
 * apps whose tabs have no description, visible text) when an app update renames an id.
 */
data class Selector(
    val ids: List<String> = emptyList(),
    val descs: List<String> = emptyList(),
    val texts: List<String> = emptyList(),
) {
    fun matchesId(node: UiNode) = node.viewId != null && node.viewId in ids

    fun matchesLabel(node: UiNode): Boolean {
        val desc = node.contentDescription
        if (desc != null && descs.any { desc.equals(it, ignoreCase = true) || desc.startsWith("$it ", ignoreCase = true) || desc.startsWith("$it,", ignoreCase = true) }) return true
        val text = node.text
        return text != null && texts.any { text.equals(it, ignoreCase = true) }
    }

    fun matches(node: UiNode) = matchesId(node) || matchesLabel(node)

    companion object {
        val NONE = Selector()
    }
}

/** Per-package selectors plus numeric tuning values, loaded from `assets/selectors.json`. */
class AppSelectors(private val selectors: Map<String, Selector>, private val numbers: Map<String, Int>) {
    operator fun get(key: String): Selector = selectors[key] ?: Selector.NONE
    fun number(key: String, default: Int): Int = numbers[key] ?: default
}

class Selectors(private val apps: Map<String, AppSelectors>) {
    fun forPackage(pkg: String): AppSelectors = apps[pkg] ?: AppSelectors(emptyMap(), emptyMap())

    companion object {
        fun parse(json: String): Selectors {
            val root = JSONObject(json)
            val apps = mutableMapOf<String, AppSelectors>()
            for (app in root.keys()) {
                if (app.startsWith("_")) continue
                val appJson = root.getJSONObject(app)
                val packages = appJson.getJSONArray("packages").strings()
                val selectorsJson = appJson.optJSONObject("selectors") ?: JSONObject()
                val selectors = selectorsJson.keys().asSequence().associateWith { key ->
                    val s = selectorsJson.getJSONObject(key)
                    Selector(
                        ids = s.optJSONArray("ids")?.strings().orEmpty(),
                        descs = s.optJSONArray("descs")?.strings().orEmpty(),
                        texts = s.optJSONArray("texts")?.strings().orEmpty(),
                    )
                }
                val numbersJson = appJson.optJSONObject("numbers") ?: JSONObject()
                val numbers = numbersJson.keys().asSequence().associateWith { numbersJson.getInt(it) }
                val appSelectors = AppSelectors(selectors, numbers)
                packages.forEach { apps[it] = appSelectors }
            }
            return Selectors(apps)
        }

        private fun org.json.JSONArray.strings() = (0 until length()).map { getString(it) }
    }
}

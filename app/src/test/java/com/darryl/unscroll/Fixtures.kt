package com.darryl.unscroll

import com.darryl.unscroll.core.AppRules
import com.darryl.unscroll.core.Bounds
import com.darryl.unscroll.core.Decision
import com.darryl.unscroll.core.ScreenContext
import com.darryl.unscroll.core.Selectors
import com.darryl.unscroll.core.Settings
import com.darryl.unscroll.core.UiNode
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** A node parsed from a uiautomator-style dump. */
class XmlUiNode(private val el: Element) : UiNode {
    private val kids: List<XmlUiNode> =
        (0 until el.childNodes.length).mapNotNull { el.childNodes.item(it) as? Element }
            .filter { it.tagName == "node" }.map(::XmlUiNode)

    override val viewId = el.getAttribute("resource-id").takeIf { it.isNotEmpty() }?.substringAfter(":id/")
    override val contentDescription = el.getAttribute("content-desc").takeIf { it.isNotEmpty() }
    override val className = el.getAttribute("class").takeIf { it.isNotEmpty() }
    override val hasText = el.getAttribute("text").isNotEmpty()
    override val text: String? = null
    override val bounds: Bounds = Regex("-?\\d+").findAll(el.getAttribute("bounds")).map { it.value.toInt() }.toList()
        .let { if (it.size == 4) Bounds(it[0], it[1], it[2], it[3]) else Bounds.EMPTY }
    override val isClickable = el.getAttribute("clickable") == "true"
    override val isSelected = el.getAttribute("selected") == "true"
    override val isScrollable = el.getAttribute("scrollable") == "true"
    override val isVisible = el.getAttribute("visible-to-user").let { it.isEmpty() || it == "true" }
    override val itemIndex = el.getAttribute("item-index").toIntOrNull() ?: -1
    override val childCount get() = kids.size
    override fun child(index: Int): UiNode? = kids.getOrNull(index)

    /** Record of taps, so tests can assert what an action would press. */
    override fun click(): Boolean = true
}

object Fixtures {
    private val root = File("src/test/resources/fixtures")
    val selectors: Selectors by lazy { Selectors.parse(File("src/main/assets/selectors.json").readText()) }
    val SCREEN = Bounds(0, 0, 1080, 2400)

    fun files(app: String, screen: String): List<File> =
        File(root, "$app/$screen").listFiles { f -> f.extension == "xml" }?.sortedBy { it.name }.orEmpty()
            .also { require(it.isNotEmpty()) { "no fixtures for $app/$screen" } }

    /** The fixture with the given label suffix, or the unlabeled one (shortest name). */
    fun file(app: String, screen: String, label: String? = null): File =
        if (label == null) files(app, screen).minBy { it.name.length }
        else files(app, screen).first { it.nameWithoutExtension.endsWith("-$label") }

    /** The first application window's root in a dump. */
    fun load(file: File): UiNode {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val windows = doc.getElementsByTagName("window")
        val container = if (windows.length > 0) windows.item(0) as Element else doc.documentElement
        val first = (0 until container.childNodes.length).map { container.childNodes.item(it) }
            .first { it is Element && it.tagName == "node" } as Element
        return XmlUiNode(first)
    }

    fun context(
        pkg: String,
        file: File,
        settings: Settings = Settings.ALL_ON,
        nowMs: Long = 100_000L,
        foregroundSinceMs: Long = 0L,
        windowClass: String? = null,
        viewerIndex: Int? = null,
    ) = ScreenContext(
        packageName = pkg,
        root = load(file),
        screen = SCREEN,
        settings = settings,
        selectors = selectors.forPackage(pkg),
        nowMs = nowMs,
        windowClass = windowClass,
        foregroundSinceMs = foregroundSinceMs,
        viewerIndex = viewerIndex,
    )

    fun decide(rules: AppRules, pkg: String, file: File, settings: Settings = Settings.ALL_ON, windowClass: String? = null): Decision =
        rules.decide(context(pkg, file, settings, windowClass = windowClass))
}

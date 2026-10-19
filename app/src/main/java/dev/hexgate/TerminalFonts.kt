package dev.hexgate

import android.content.Context
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

data class TerminalFont(val id: String, val label: String, val asset: String)

/** Only bundled, licensed monospace faces are selectable. Load on an IO dispatcher. */
object TerminalFonts {
    val choices = listOf(
        TerminalFont("jetbrains", "JetBrains Mono", "fonts/JetBrainsMonoNerdFontMono-Regular.ttf"),
        TerminalFont("fira", "Fira Code", "fonts/FiraCodeNerdFontMono-Regular.ttf"),
        TerminalFont("source", "Source Code Pro", "fonts/SauceCodeProNerdFontMono-Regular.ttf"),
    )
    private val cache = ConcurrentHashMap<String, Typeface>()
    fun resolve(id: String) = choices.firstOrNull { it.id == id } ?: choices.first()
    fun load(context: Context, id: String): Typeface {
        val font = resolve(id)
        return cache.getOrPut(font.id) { Typeface.createFromAsset(context.assets, font.asset) }
    }
}

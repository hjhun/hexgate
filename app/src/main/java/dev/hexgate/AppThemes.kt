package dev.hexgate

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Fixed local color data; public palettes and notices are bundled with the APK. */
data class AppTheme(val id: String, val label: String, val light: Boolean, val background: Int,
    val foreground: Int, val accent: Int, val ansi: IntArray = intArrayOf()) {
    fun css(color: Int) = "#%06x".format(color and 0xffffff)
}
object AppThemes {
    val presets = listOf(
        AppTheme("hexgate", "Hexgate Dark", false, 0xff0b1119.toInt(), 0xffe5ebf2.toInt(), 0xffe7c482.toInt()),
        AppTheme("light", "Light", true, 0xfff5f7fa.toInt(), 0xff17212e.toInt(), 0xff765518.toInt()),
        AppTheme("amber", "Amber", false, 0xff151108.toInt(), 0xffffdc83.toInt(), 0xffffc94a.toInt()),
        AppTheme("dracula", "Dracula", false, 0xff282a36.toInt(), 0xfff8f8f2.toInt(), 0xffbd93f9.toInt(), intArrayOf(0xff21222c.toInt(),0xffff5555.toInt(),0xff50fa7b.toInt(),0xfff1fa8c.toInt(),0xffbd93f9.toInt(),0xffff79c6.toInt(),0xff8be9fd.toInt(),0xfff8f8f2.toInt(),0xff6272a4.toInt(),0xffff6e6e.toInt(),0xff69ff94.toInt(),0xffffffa5.toInt(),0xffd6acff.toInt(),0xffff92df.toInt(),0xffa4ffff.toInt(),0xffffffff.toInt())),
        AppTheme("gruvbox", "Gruvbox Dark", false, 0xff282828.toInt(), 0xffebdbb2.toInt(), 0xfffabd2f.toInt(), intArrayOf(0xff282828.toInt(),0xffcc241d.toInt(),0xff98971a.toInt(),0xffd79921.toInt(),0xff458588.toInt(),0xffb16286.toInt(),0xff689d6a.toInt(),0xffa89984.toInt(),0xff928374.toInt(),0xfffb4934.toInt(),0xffb8bb26.toInt(),0xfffabd2f.toInt(),0xff83a598.toInt(),0xffd3869b.toInt(),0xff8ec07c.toInt(),0xffebdbb2.toInt())),
        AppTheme("solarized", "Solarized Dark", false, 0xff001e27.toInt(), 0xff93a1a1.toInt(), 0xff2aa198.toInt(), intArrayOf(0xff002831.toInt(),0xffd11c24.toInt(),0xff738a05.toInt(),0xffa57706.toInt(),0xff2176c7.toInt(),0xffc61c6f.toInt(),0xff259286.toInt(),0xffeae3cb.toInt(),0xff001e27.toInt(),0xffbd3613.toInt(),0xff475b62.toInt(),0xff536870.toInt(),0xff708284.toInt(),0xff5956ba.toInt(),0xff819090.toInt(),0xfffcf4dc.toInt()))
    )
    fun validId(id: String) = if(id=="system" || presets.any { it.id==id }) id else "hexgate"
    fun resolve(id: String, systemDark: Boolean = true): AppTheme =
        presets.first { it.id == if(id=="system") { if(systemDark) "hexgate" else "light" } else validId(id) }
    fun blend(first: Int, second: Int, weight: Float): Int {
        fun channel(shift: Int) = (((first ushr shift) and 255)*(1-weight)+((second ushr shift) and 255)*weight).toInt()
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    fun ink(background: Int) = if(Color(background).luminance()>.35f) 0xff101820.toInt() else 0xfff8fafc.toInt()
}
val LocalAppTheme = staticCompositionLocalOf { AppThemes.resolve("hexgate") }

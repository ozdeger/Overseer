package games.ace.overseer.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser
import java.awt.Color

/** Converts markdown to a full, theme-aware HTML document (rendered by JCEF for proper styling). */
object MarkdownRenderer {

    fun toHtml(markdown: String): String {
        val flavour = GFMFlavourDescriptor()
        val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        val body = HtmlGenerator(markdown, tree, flavour).generateHtml()
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>${css()}</style></head>$body</html>"
    }

    private fun hex(c: Color): String = String.format("#%02x%02x%02x", c.red, c.green, c.blue)

    private fun css(): String {
        val dark = !JBColor.isBright()
        val bg = hex(UIUtil.getPanelBackground())
        val fg = hex(UIUtil.getLabelForeground())
        val codeBg = if (dark) "#2b2d30" else "#f0f1f2"
        val border = if (dark) "#3c3f41" else "#d0d7de"
        val link = if (dark) "#5a9cf8" else "#0969da"
        val muted = if (dark) "#9aa0a6" else "#656d76"
        val headerBg = if (dark) "#2b2d30" else "#f6f8fa"
        val zebra = if (dark) "#26282e" else "#f6f8fa"
        return """
            * { box-sizing: border-box; }
            body { margin: 0; padding: 12px 16px; background: $bg; color: $fg;
                   font-family: -apple-system, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                   font-size: 13px; line-height: 1.55; word-wrap: break-word; }
            h1, h2, h3, h4, h5 { font-weight: 600; line-height: 1.25; margin: 18px 0 10px; }
            h1 { font-size: 1.7em; padding-bottom: .3em; border-bottom: 1px solid $border; }
            h2 { font-size: 1.4em; padding-bottom: .3em; border-bottom: 1px solid $border; }
            h3 { font-size: 1.2em; }
            h4 { font-size: 1.05em; }
            p { margin: 0 0 10px; }
            a { color: $link; text-decoration: none; }
            a:hover { text-decoration: underline; }
            ul, ol { margin: 0 0 10px; padding-left: 1.6em; }
            li { margin: 3px 0; }
            blockquote { margin: 0 0 10px; padding: 0 1em; color: $muted; border-left: .25em solid $border; }
            code { font-family: 'JetBrains Mono', Consolas, 'Courier New', monospace; font-size: 85%;
                   background: $codeBg; padding: .15em .4em; border-radius: 4px; }
            pre { background: $codeBg; padding: 12px; border-radius: 6px; overflow: auto; margin: 0 0 12px; }
            pre code { background: transparent; padding: 0; font-size: 90%; }
            table { border-collapse: collapse; margin: 0 0 12px; display: block; width: max-content; max-width: 100%; overflow: auto; }
            th, td { border: 1px solid $border; padding: 6px 12px; text-align: left; vertical-align: top; }
            th { background: $headerBg; font-weight: 600; }
            tr:nth-child(2n) td { background: $zebra; }
            hr { height: 1px; border: 0; background: $border; margin: 16px 0; }
            img { max-width: 100%; }
        """.trimIndent()
    }
}

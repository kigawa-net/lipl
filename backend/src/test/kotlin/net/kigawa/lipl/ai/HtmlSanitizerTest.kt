package net.kigawa.lipl.ai

import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlSanitizerTest {

    @Test
    fun `keeps object-fit so images do not get squished when width and height are fixed`() {
        val raw = """<img src="https://example.com/a.jpg" alt="a" style="width:100%;height:240px;object-fit:cover;">"""

        val sanitized = sanitizeGeneratedHtml(raw)

        assertEquals(
            """<img src="https://example.com/a.jpg" alt="a" style="width:100%;height:240px;object-fit:cover" />""",
            sanitized,
        )
    }

    @Test
    fun `rejects an unknown object-fit value`() {
        val raw = """<img src="https://example.com/a.jpg" alt="a" style="object-fit:not-a-real-value;">"""

        val sanitized = sanitizeGeneratedHtml(raw)

        assertEquals("""<img src="https://example.com/a.jpg" alt="a" />""", sanitized)
    }

    @Test
    fun `keeps display and overflow keywords`() {
        val raw = """<div style="display:flex;overflow:hidden;overflow-x:auto;overflow-y:scroll;"></div>"""

        val sanitized = sanitizeGeneratedHtml(raw)

        assertEquals(
            """<div style="display:flex;overflow:hidden;overflow-x:auto;overflow-y:scroll"></div>""",
            sanitized,
        )
    }

    @Test
    fun `keeps a tel link so the call-to-reserve button works`() {
        val raw = """<a href="tel:0312345678">今すぐ電話で予約</a>"""

        val sanitized = sanitizeGeneratedHtml(raw)

        assertEquals("""<a href="tel:0312345678">今すぐ電話で予約</a>""", sanitized)
    }

    @Test
    fun `still keeps http and https links and strips javascript links`() {
        assertEquals(
            """<a href="https://example.com">リンク</a>""",
            sanitizeGeneratedHtml("""<a href="https://example.com">リンク</a>"""),
        )
        assertEquals("危険", sanitizeGeneratedHtml("""<a href="javascript:alert(1)">危険</a>"""))
    }

    @Test
    fun `still strips scripts and event handlers`() {
        val raw = """<div onclick="alert(1)"><script>alert(1)</script>本文</div>"""

        val sanitized = sanitizeGeneratedHtml(raw)

        assertEquals("<div>本文</div>", sanitized)
    }
}

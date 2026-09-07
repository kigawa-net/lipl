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
    fun `still strips scripts and event handlers`() {
        val raw = """<div onclick="alert(1)"><script>alert(1)</script>本文</div>"""

        val sanitized = sanitizeGeneratedHtml(raw)

        assertEquals("<div>本文</div>", sanitized)
    }
}

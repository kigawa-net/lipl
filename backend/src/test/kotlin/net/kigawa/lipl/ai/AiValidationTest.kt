package net.kigawa.lipl.ai

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AiValidationTest {

    @Test
    fun `blank catchphrase is rejected`() {
        assertFailsWith<LpContentValidationException> {
            UpdateLpContentRequest(catchphrase = " ", pageHtml = "<p>本文</p>").validate()
        }
    }

    @Test
    fun `catchphrase over 200 characters is rejected`() {
        assertFailsWith<LpContentValidationException> {
            UpdateLpContentRequest(catchphrase = "あ".repeat(201), pageHtml = "<p>本文</p>").validate()
        }
    }

    @Test
    fun `blank pageHtml is rejected`() {
        assertFailsWith<LpContentValidationException> {
            UpdateLpContentRequest(catchphrase = "キャッチコピー", pageHtml = " ").validate()
        }
    }

    @Test
    fun `pageHtml over 20000 characters is rejected`() {
        assertFailsWith<LpContentValidationException> {
            UpdateLpContentRequest(catchphrase = "キャッチコピー", pageHtml = "あ".repeat(20001)).validate()
        }
    }

    @Test
    fun `valid request does not throw`() {
        UpdateLpContentRequest(catchphrase = "キャッチコピー", pageHtml = "<p>本文</p>").validate()
    }
}

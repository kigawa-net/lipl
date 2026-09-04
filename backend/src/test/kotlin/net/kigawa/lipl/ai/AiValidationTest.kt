package net.kigawa.lipl.ai

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AiValidationTest {

    @Test
    fun `blank catchphrase is rejected`() {
        assertFailsWith<LpContentValidationException> {
            UpdateLpContentRequest(catchphrase = " ", description = "説明文").validate()
        }
    }

    @Test
    fun `catchphrase over 200 characters is rejected`() {
        assertFailsWith<LpContentValidationException> {
            UpdateLpContentRequest(catchphrase = "あ".repeat(201), description = "説明文").validate()
        }
    }

    @Test
    fun `blank description is rejected`() {
        assertFailsWith<LpContentValidationException> {
            UpdateLpContentRequest(catchphrase = "キャッチコピー", description = " ").validate()
        }
    }

    @Test
    fun `description over 2000 characters is rejected`() {
        assertFailsWith<LpContentValidationException> {
            UpdateLpContentRequest(catchphrase = "キャッチコピー", description = "あ".repeat(2001)).validate()
        }
    }

    @Test
    fun `valid request does not throw`() {
        UpdateLpContentRequest(catchphrase = "キャッチコピー", description = "説明文").validate()
    }
}

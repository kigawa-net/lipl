package net.kigawa.lipl.menu

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MenuValidationTest {

    @Test
    fun `valid menu item passes validation`() {
        CreateMenuItemRequest(name = "カレー", price = 800, description = "自家製カレー").validate()
    }

    @Test
    fun `blank name is rejected`() {
        assertFailsWith<MenuItemValidationException> {
            CreateMenuItemRequest(name = "  ").validate()
        }
    }

    @Test
    fun `name over 50 chars is rejected`() {
        assertFailsWith<MenuItemValidationException> {
            CreateMenuItemRequest(name = "あ".repeat(51)).validate()
        }
    }

    @Test
    fun `description over 200 chars is rejected`() {
        assertFailsWith<MenuItemValidationException> {
            CreateMenuItemRequest(name = "カレー", description = "あ".repeat(201)).validate()
        }
    }

    @Test
    fun `negative price is rejected`() {
        assertFailsWith<MenuItemValidationException> {
            CreateMenuItemRequest(name = "カレー", price = -1).validate()
        }
    }

    @Test
    fun `null price and description are allowed`() {
        CreateMenuItemRequest(name = "カレー").validate()
    }
}

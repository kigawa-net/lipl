package net.kigawa.lipl.store

import kotlin.test.Test
import kotlin.test.assertFailsWith

class StoreValidationTest {

    private fun baseRequest(
        name: String = "テストカフェ",
        businessCategory: BusinessCategory = BusinessCategory.CAFE,
        operationType: OperationType? = null,
        address: String? = "愛知県名古屋市中区1-1-1",
        businessArea: String? = null,
        snsLinks: List<SnsLinkInput> = emptyList(),
    ) = CreateStoreRequest(
        name = name,
        businessCategory = businessCategory,
        operationType = operationType,
        address = address,
        businessArea = businessArea,
        snsLinks = snsLinks,
    )

    @Test
    fun `valid fixed store passes validation`() {
        baseRequest().validate()
    }

    @Test
    fun `blank name is rejected`() {
        assertFailsWith<StoreValidationException> {
            baseRequest(name = "  ").validate()
        }
    }

    @Test
    fun `name over 50 chars is rejected`() {
        assertFailsWith<StoreValidationException> {
            baseRequest(name = "あ".repeat(51)).validate()
        }
    }

    @Test
    fun `fixed store without address is rejected`() {
        assertFailsWith<StoreValidationException> {
            baseRequest(address = null).validate()
        }
    }

    @Test
    fun `mobile store without business area is rejected`() {
        assertFailsWith<StoreValidationException> {
            baseRequest(
                businessCategory = BusinessCategory.KITCHEN_CAR,
                operationType = OperationType.MOBILE,
                address = null,
                businessArea = null,
            ).validate()
        }
    }

    @Test
    fun `mobile store with business area passes`() {
        baseRequest(
            businessCategory = BusinessCategory.KITCHEN_CAR,
            operationType = OperationType.MOBILE,
            address = null,
            businessArea = "名古屋市内中心",
        ).validate()
    }

    @Test
    fun `kitchen car defaults to mobile operation type`() {
        val defaulted = defaultOperationTypeFor(BusinessCategory.KITCHEN_CAR)
        kotlin.test.assertEquals(OperationType.MOBILE, defaulted)
    }

    @Test
    fun `non kitchen car defaults to fixed operation type`() {
        val defaulted = defaultOperationTypeFor(BusinessCategory.CAFE)
        kotlin.test.assertEquals(OperationType.FIXED, defaulted)
    }

    @Test
    fun `duplicate sns platform is rejected`() {
        assertFailsWith<StoreValidationException> {
            baseRequest(
                snsLinks = listOf(
                    SnsLinkInput(SnsPlatform.INSTAGRAM, "https://instagram.com/a"),
                    SnsLinkInput(SnsPlatform.INSTAGRAM, "https://instagram.com/b"),
                ),
            ).validate()
        }
    }

    @Test
    fun `sns url over 500 chars is rejected`() {
        assertFailsWith<StoreValidationException> {
            baseRequest(
                snsLinks = listOf(
                    SnsLinkInput(SnsPlatform.INSTAGRAM, "https://instagram.com/" + "a".repeat(500)),
                ),
            ).validate()
        }
    }

}

package net.kigawa.lipl.store

import kotlinx.serialization.Serializable
import net.kigawa.lipl.ai.LpContentResponse
import net.kigawa.lipl.menu.MenuItemResponse
import net.kigawa.lipl.photo.PhotoResponse

@Serializable
enum class BusinessCategory {
    CAFE, IZAKAYA, RAMEN, RESTAURANT, KITCHEN_CAR, BAR, TEISHOKU, OTHER
}

@Serializable
enum class OperationType {
    FIXED, MOBILE
}

@Serializable
enum class SnsPlatform {
    INSTAGRAM, X, FACEBOOK, LINE, TIKTOK, YOUTUBE
}

fun defaultOperationTypeFor(category: BusinessCategory): OperationType =
    if (category == BusinessCategory.KITCHEN_CAR) OperationType.MOBILE else OperationType.FIXED

@Serializable
data class SnsLinkInput(
    val platform: SnsPlatform,
    val url: String,
)

@Serializable
data class CreateStoreRequest(
    val name: String,
    val businessCategory: BusinessCategory,
    val operationType: OperationType? = null,
    val address: String? = null,
    val businessArea: String? = null,
    val businessHours: String? = null,
    val phone: String? = null,
    val snsLinks: List<SnsLinkInput> = emptyList(),
)

@Serializable
data class StoreResponse(
    val id: Long,
    val slug: String,
    val name: String,
    val businessCategory: BusinessCategory,
    val operationType: OperationType,
    val address: String?,
    val businessArea: String?,
    val businessHours: String?,
    val phone: String?,
    val snsLinks: List<SnsLinkInput>,
    val published: Boolean,
)

@Serializable
data class UpdatePublishedRequest(
    val published: Boolean,
)

// 公開ページ（/p/{slug}）が返す情報。ownerSub等の内部情報は含まない。
@Serializable
data class PublicStoreResponse(
    val name: String,
    val businessCategory: BusinessCategory,
    val operationType: OperationType,
    val address: String?,
    val businessArea: String?,
    val businessHours: String?,
    val phone: String?,
    val snsLinks: List<SnsLinkInput>,
    val menuItems: List<MenuItemResponse>,
    val photos: List<PhotoResponse>,
    val lpContent: LpContentResponse?,
    val kaftBaseUrl: String,
)

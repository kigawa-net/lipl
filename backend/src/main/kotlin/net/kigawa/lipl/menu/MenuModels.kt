package net.kigawa.lipl.menu

import kotlinx.serialization.Serializable

// TODO: 現在は課金プラン（Free/Basic/Pro）が未実装のため、全店舗をFreeプラン
// （上限5品）として扱う。プラン機能実装時にストアのプランに応じた上限に置き換える。
const val FREE_PLAN_MENU_ITEM_LIMIT = 5

@Serializable
data class CreateMenuItemRequest(
    val name: String,
    val price: Int? = null,
    val description: String? = null,
)

@Serializable
data class MenuItemResponse(
    val id: Long,
    val storeId: Long,
    val name: String,
    val price: Int?,
    val description: String?,
    val displayOrder: Int,
    val photoKaftUuid: String?,
    val photoFilename: String?,
)

@Serializable
data class ReorderMenuItemsRequest(
    val orderedIds: List<Long>,
)

@Serializable
data class MenuItemUploadTokenResponse(
    val uuid: String,
    val uploadToken: String,
    val kaftBaseUrl: String,
)

@Serializable
data class ConfirmMenuItemPhotoRequest(
    val uuid: String,
    val filename: String,
)

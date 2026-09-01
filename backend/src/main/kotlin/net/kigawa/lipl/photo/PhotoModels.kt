package net.kigawa.lipl.photo

import kotlinx.serialization.Serializable

// TODO: 現在は課金プラン（Free/Basic/Pro）が未実装のため、全店舗をFreeプラン
// （上限15枚）として扱う。プラン機能実装時にストアのプランに応じた上限に置き換える。
const val FREE_PLAN_PHOTO_LIMIT = 15

// 実際のアップロードはブラウザからkaftへ直接行われ、lipl backendは
// バイト列を経由しない（アーキテクチャ上の制約）。そのため
// フォーマット・サイズ上限（JPEG/PNG/WebP、10MB、解像度上限リサイズ）は
// 現時点ではフロントエンド側の一次チェックのみで、サーバー側での
// 強制はできていない（既知の制約。将来kaft側でのバリデーション追加や
// Ingressでのボディサイズ制限を検討）。

@Serializable
data class UploadTokenResponse(
    val uuid: String,
    val uploadToken: String,
    val kaftBaseUrl: String,
)

@Serializable
data class ConfirmPhotoRequest(
    val uuid: String,
    val filename: String,
)

@Serializable
data class PhotoResponse(
    val id: Long,
    val storeId: Long,
    val kaftUuid: String,
    val filename: String,
    val displayOrder: Int,
)

@Serializable
data class ReorderPhotosRequest(
    val orderedIds: List<Long>,
)

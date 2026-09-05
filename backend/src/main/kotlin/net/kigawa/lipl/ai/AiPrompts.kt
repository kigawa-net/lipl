package net.kigawa.lipl.ai

import net.kigawa.lipl.menu.MenuItemResponse
import net.kigawa.lipl.photo.PhotoResponse
import net.kigawa.lipl.store.StoreResponse
import java.net.URLEncoder

internal const val INTERVIEW_SYSTEM_PROMPT = """
あなたは飲食店のランディングページ作成を支援するインタビュアーです。
店舗の基本情報をもとに、お店の魅力やこだわりを引き出すオープンな質問を1つだけ日本語で行ってください。
質問文のみを出力し、挨拶や前置き、番号付けは含めないでください。
"""

internal const val GENERATION_SYSTEM_PROMPT = """
あなたは飲食店のランディングページを作成するWebデザイナー兼コピーライターです。
店舗の基本情報・メニュー・写真URL・ヒアリングの質疑応答をもとに、日本語で
キャッチコピーと、公開ランディングページ本体のHTMLを作成してください。

HTMLの制約:
- <html>・<head>・<body>・<script>・<style>タグは使わず、装飾は各要素のstyle属性（インラインCSS）で行うこと
- 外部リソース（フォント・画像・CSS等）は読み込まないこと。画像は与えられた写真URLのみを、与えられたとおりそのまま使うこと
- 与えられていない情報（存在しない住所・電話番号・メニュー・写真URL等）を創作しないこと
- 与えられたメニュー・写真はできるだけ全て使うこと
- 見出し・写真・紹介文・メニュー一覧・営業時間・電話番号・SNSリンクを含む、来店したくなる一つの完結したページにすること

出力は必ず次のJSON形式のみで返し、他の文章・コードブロック記法・説明は一切含めないでください。
{"catchphrase": "30文字程度のキャッチコピー", "pageHtml": "ページ本体のHTML（文字列、改行はエスケープ）"}
"""

internal fun storeContextText(store: StoreResponse): String = buildString {
    appendLine("店舗名: ${store.name}")
    appendLine("業種: ${store.businessCategory}")
    appendLine("所在地/出店エリア: ${store.address ?: store.businessArea ?: "未設定"}")
    if (!store.businessHours.isNullOrBlank()) appendLine("営業時間: ${store.businessHours}")
}

internal fun photoUrl(kaftBaseUrl: String, photo: PhotoResponse): String {
    val encodedFilename = URLEncoder.encode(photo.filename, "UTF-8").replace("+", "%20")
    return "$kaftBaseUrl/files/${photo.kaftUuid}/$encodedFilename"
}

internal fun buildGenerationContext(
    store: StoreResponse,
    menuItems: List<MenuItemResponse>,
    photos: List<PhotoResponse>,
    kaftBaseUrl: String,
): String = buildString {
    appendLine("店舗名: ${store.name}")
    appendLine("業種: ${store.businessCategory}")
    appendLine("所在地/出店エリア: ${store.address ?: store.businessArea ?: "未設定"}")
    if (!store.businessHours.isNullOrBlank()) appendLine("営業時間: ${store.businessHours}")
    if (!store.phone.isNullOrBlank()) appendLine("電話番号: ${store.phone}")
    if (store.snsLinks.isNotEmpty()) {
        appendLine("SNSリンク:")
        store.snsLinks.forEach { appendLine("- ${it.platform}: ${it.url}") }
    }
    if (menuItems.isNotEmpty()) {
        appendLine("メニュー:")
        menuItems.forEach { item ->
            append("- ${item.name}")
            item.price?.let { append("（¥$it）") }
            item.description?.let { append(" $it") }
            photos.find { it.id == item.photoId }?.let { append(" [写真: ${photoUrl(kaftBaseUrl, it)}]") }
            appendLine()
        }
    }
    if (photos.isNotEmpty()) {
        appendLine("店舗写真URL一覧（ヒーロー画像等に使用可）:")
        photos.forEach { appendLine("- ${photoUrl(kaftBaseUrl, it)}") }
    }
}

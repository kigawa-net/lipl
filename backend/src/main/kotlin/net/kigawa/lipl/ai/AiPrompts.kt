package net.kigawa.lipl.ai

import net.kigawa.lipl.store.StoreResponse

internal const val INTERVIEW_SYSTEM_PROMPT = """
あなたは飲食店のランディングページ作成を支援するインタビュアーです。
店舗の基本情報をもとに、お店の魅力やこだわりを引き出すオープンな質問を1つだけ日本語で行ってください。
質問文のみを出力し、挨拶や前置き、番号付けは含めないでください。
"""

internal const val GENERATION_SYSTEM_PROMPT = """
あなたは飲食店のランディングページ用の文章を作成するコピーライターです。
店舗の基本情報とヒアリングの質疑応答をもとに、日本語でキャッチコピーと紹介文を作成してください。
出力は必ず次のJSON形式のみで返し、他の文章・コードブロック記法・説明は一切含めないでください。
{"catchphrase": "30文字程度のキャッチコピー", "description": "150〜200文字程度の紹介文"}
"""

internal fun storeContextText(store: StoreResponse): String = buildString {
    appendLine("店舗名: ${store.name}")
    appendLine("業種: ${store.businessCategory}")
    appendLine("所在地/出店エリア: ${store.address ?: store.businessArea ?: "未設定"}")
    if (!store.businessHours.isNullOrBlank()) appendLine("営業時間: ${store.businessHours}")
}

package net.kigawa.lipl.ai

class LpContentValidationException(message: String) : Exception(message)

fun UpdateLpContentRequest.validate() {
    if (catchphrase.isBlank()) throw LpContentValidationException("キャッチコピーは必須です")
    if (catchphrase.length > 200) throw LpContentValidationException("キャッチコピーは200文字以内で入力してください")
    if (pageHtml.isBlank()) throw LpContentValidationException("ページ内容は必須です")
    if (pageHtml.length > 20000) throw LpContentValidationException("ページ内容は20000文字以内で入力してください")
}

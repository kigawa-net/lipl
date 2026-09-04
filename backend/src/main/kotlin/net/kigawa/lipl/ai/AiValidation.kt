package net.kigawa.lipl.ai

class LpContentValidationException(message: String) : Exception(message)

fun UpdateLpContentRequest.validate() {
    if (catchphrase.isBlank()) throw LpContentValidationException("キャッチコピーは必須です")
    if (catchphrase.length > 200) throw LpContentValidationException("キャッチコピーは200文字以内で入力してください")
    if (description.isBlank()) throw LpContentValidationException("紹介文は必須です")
    if (description.length > 2000) throw LpContentValidationException("紹介文は2000文字以内で入力してください")
}

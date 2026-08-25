package net.kigawa.lipl.store

class StoreValidationException(message: String) : Exception(message)

fun CreateStoreRequest.validate() {
    if (name.isBlank()) throw StoreValidationException("店名は必須です")
    if (name.length > 50) throw StoreValidationException("店名は50文字以内で入力してください")

    val operationType = operationType ?: defaultOperationTypeFor(businessCategory)
    when (operationType) {
        OperationType.FIXED -> {
            if (address.isNullOrBlank()) {
                throw StoreValidationException("固定店舗の場合、所在地（住所）は必須です")
            }
        }
        OperationType.MOBILE -> {
            if (businessArea.isNullOrBlank()) {
                throw StoreValidationException("移動販売の場合、出店エリアは必須です")
            }
        }
    }

    address?.let { if (it.length > 200) throw StoreValidationException("所在地は200文字以内で入力してください") }
    businessArea?.let { if (it.length > 200) throw StoreValidationException("出店エリアは200文字以内で入力してください") }
    businessHours?.let { if (it.length > 200) throw StoreValidationException("営業時間は200文字以内で入力してください") }
    phone?.let { if (it.length > 20) throw StoreValidationException("電話番号は20文字以内で入力してください") }

    snsLinks.forEach { link ->
        if (link.url.length > 500) throw StoreValidationException("SNSリンクのURLは500文字以内で入力してください")
    }
    val duplicatePlatforms = snsLinks.groupBy { it.platform }.filterValues { it.size > 1 }.keys
    if (duplicatePlatforms.isNotEmpty()) {
        throw StoreValidationException("同一プラットフォームのSNSリンクは1件までです: ${duplicatePlatforms.joinToString()}")
    }
}

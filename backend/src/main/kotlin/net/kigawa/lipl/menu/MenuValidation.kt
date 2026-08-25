package net.kigawa.lipl.menu

class MenuItemValidationException(message: String) : Exception(message)

fun CreateMenuItemRequest.validate() {
    if (name.isBlank()) throw MenuItemValidationException("品名は必須です")
    if (name.length > 50) throw MenuItemValidationException("品名は50文字以内で入力してください")
    description?.let {
        if (it.length > 200) throw MenuItemValidationException("説明文は200文字以内で入力してください")
    }
    price?.let {
        if (it < 0) throw MenuItemValidationException("価格は0以上で入力してください")
    }
}

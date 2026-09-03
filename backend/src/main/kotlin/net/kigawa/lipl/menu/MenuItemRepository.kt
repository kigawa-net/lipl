package net.kigawa.lipl.menu

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class MenuItemLimitExceededException(limit: Int) :
    Exception("メニューは$limit 品まで登録できます")

class MenuItemNotFoundException : Exception("メニューが見つかりません")

class MenuItemRepository {

    fun listByStore(storeId: Long): List<MenuItemResponse> = transaction {
        MenuItemsTable.selectAll()
            .andWhere { MenuItemsTable.storeId eq storeId }
            .orderBy(MenuItemsTable.displayOrder to SortOrder.ASC)
            .map { it.toResponse() }
    }

    fun create(storeId: Long, request: CreateMenuItemRequest): MenuItemResponse = transaction {
        val currentCount = MenuItemsTable.selectAll().andWhere { MenuItemsTable.storeId eq storeId }.count()
        if (currentCount >= FREE_PLAN_MENU_ITEM_LIMIT) {
            throw MenuItemLimitExceededException(FREE_PLAN_MENU_ITEM_LIMIT)
        }

        val nextOrder = (MenuItemsTable.selectAll().andWhere { MenuItemsTable.storeId eq storeId }
            .maxOfOrNull { it[MenuItemsTable.displayOrder] } ?: -1) + 1

        val id = MenuItemsTable.insert {
            it[MenuItemsTable.storeId] = storeId
            it[name] = request.name
            it[price] = request.price
            it[description] = request.description
            it[displayOrder] = nextOrder
        } get MenuItemsTable.id

        toResponse(id)
    }

    // 削除した品目に写真が設定されていた場合、そのkaftUuidを返す（呼び出し側でkaftからも削除する）。
    fun delete(storeId: Long, menuItemId: Long): String? = transaction {
        val row = MenuItemsTable.selectAll()
            .andWhere { MenuItemsTable.id eq menuItemId }
            .andWhere { MenuItemsTable.storeId eq storeId }
            .singleOrNull() ?: throw MenuItemNotFoundException()
        MenuItemsTable.deleteWhere { MenuItemsTable.id eq menuItemId }
        row[MenuItemsTable.photoKaftUuid]
    }

    // 店舗削除時に呼び出す。削除した品目のうち写真が設定されていたもののkaftUuid一覧を返す。
    fun deleteByStore(storeId: Long): List<String> = transaction {
        val kaftUuids = MenuItemsTable.selectAll()
            .andWhere { MenuItemsTable.storeId eq storeId }
            .mapNotNull { it[MenuItemsTable.photoKaftUuid] }
        MenuItemsTable.deleteWhere { MenuItemsTable.storeId eq storeId }
        kaftUuids
    }

    // 写真を設定する。既に写真が設定されていた場合は古いkaftUuidを返す（呼び出し側でkaftから削除する）。
    fun setPhoto(storeId: Long, menuItemId: Long, kaftUuid: String, filename: String): Pair<MenuItemResponse, String?> =
        transaction {
            val row = MenuItemsTable.selectAll()
                .andWhere { MenuItemsTable.id eq menuItemId }
                .andWhere { MenuItemsTable.storeId eq storeId }
                .singleOrNull() ?: throw MenuItemNotFoundException()
            val previousKaftUuid = row[MenuItemsTable.photoKaftUuid]

            MenuItemsTable.update({ MenuItemsTable.id eq menuItemId }) {
                it[photoKaftUuid] = kaftUuid
                it[photoFilename] = filename
            }

            toResponse(menuItemId) to previousKaftUuid
        }

    // 写真を削除する。設定されていた場合は古いkaftUuidを返す（呼び出し側でkaftから削除する）。
    fun clearPhoto(storeId: Long, menuItemId: Long): String? = transaction {
        val row = MenuItemsTable.selectAll()
            .andWhere { MenuItemsTable.id eq menuItemId }
            .andWhere { MenuItemsTable.storeId eq storeId }
            .singleOrNull() ?: throw MenuItemNotFoundException()
        val previousKaftUuid = row[MenuItemsTable.photoKaftUuid]

        MenuItemsTable.update({ MenuItemsTable.id eq menuItemId }) {
            it[photoKaftUuid] = null
            it[photoFilename] = null
        }

        previousKaftUuid
    }

    fun reorder(storeId: Long, orderedIds: List<Long>) = transaction {
        val existingIds = MenuItemsTable.selectAll().andWhere { MenuItemsTable.storeId eq storeId }
            .map { it[MenuItemsTable.id] }
            .toSet()
        if (existingIds != orderedIds.toSet()) {
            throw MenuItemNotFoundException()
        }

        orderedIds.forEachIndexed { index, itemId ->
            MenuItemsTable.update({ MenuItemsTable.id eq itemId }) {
                it[displayOrder] = index
            }
        }
    }

    private fun toResponse(menuItemId: Long): MenuItemResponse {
        val row = MenuItemsTable.selectAll().andWhere { MenuItemsTable.id eq menuItemId }.single()
        return row.toResponse()
    }

    private fun ResultRow.toResponse(): MenuItemResponse = MenuItemResponse(
        id = this[MenuItemsTable.id],
        storeId = this[MenuItemsTable.storeId],
        name = this[MenuItemsTable.name],
        price = this[MenuItemsTable.price],
        description = this[MenuItemsTable.description],
        displayOrder = this[MenuItemsTable.displayOrder],
        photoKaftUuid = this[MenuItemsTable.photoKaftUuid],
        photoFilename = this[MenuItemsTable.photoFilename],
    )
}

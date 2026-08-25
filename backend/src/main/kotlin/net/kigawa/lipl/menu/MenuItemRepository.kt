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

    fun delete(storeId: Long, menuItemId: Long) = transaction {
        val exists = MenuItemsTable.selectAll()
            .andWhere { MenuItemsTable.id eq menuItemId }
            .andWhere { MenuItemsTable.storeId eq storeId }
            .any()
        if (!exists) {
            throw MenuItemNotFoundException()
        }
        MenuItemsTable.deleteWhere { MenuItemsTable.id eq menuItemId }
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
    )
}

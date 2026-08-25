package net.kigawa.lipl.menu

import net.kigawa.lipl.store.StoresTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object MenuItemsTable : Table("menu_items") {
    val id = long("id").autoIncrement()
    val storeId = long("store_id").references(StoresTable.id)
    val name = varchar("name", 50)
    val price = integer("price").nullable()
    val description = varchar("description", 200).nullable()
    val displayOrder = integer("display_order")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

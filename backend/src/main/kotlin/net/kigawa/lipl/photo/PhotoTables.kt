package net.kigawa.lipl.photo

import net.kigawa.lipl.store.StoresTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object PhotosTable : Table("photos") {
    val id = long("id").autoIncrement()
    val storeId = long("store_id").references(StoresTable.id)
    val kaftUuid = varchar("kaft_uuid", 36)
    val filename = varchar("filename", 255)
    val displayOrder = integer("display_order")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

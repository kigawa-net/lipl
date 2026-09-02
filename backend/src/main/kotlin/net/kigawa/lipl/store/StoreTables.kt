package net.kigawa.lipl.store

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object StoresTable : Table("stores") {
    val id = long("id").autoIncrement()
    val ownerSub = varchar("owner_sub", 255)
    val slug = varchar("slug", 100).uniqueIndex()
    val name = varchar("name", 50)
    val businessCategory = varchar("business_category", 20)
    val operationType = varchar("operation_type", 10)
    val address = varchar("address", 200).nullable()
    val businessArea = varchar("business_area", 200).nullable()
    val businessHours = varchar("business_hours", 200).nullable()
    val phone = varchar("phone", 20).nullable()
    val published = bool("published").default(false)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object StoreSnsLinksTable : Table("store_sns_links") {
    val id = long("id").autoIncrement()
    val storeId = long("store_id").references(StoresTable.id)
    val platform = varchar("platform", 20)
    val url = varchar("url", 500)

    override val primaryKey = PrimaryKey(id)
}

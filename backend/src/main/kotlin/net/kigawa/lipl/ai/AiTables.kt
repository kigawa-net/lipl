package net.kigawa.lipl.ai

import net.kigawa.lipl.store.StoresTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object InterviewMessagesTable : Table("interview_messages") {
    val id = long("id").autoIncrement()
    val storeId = long("store_id").references(StoresTable.id, onDelete = ReferenceOption.CASCADE)
    val role = varchar("role", 10)
    val content = varchar("content", 2000)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(id)
}

object LpContentsTable : Table("lp_contents") {
    val storeId = long("store_id").references(StoresTable.id, onDelete = ReferenceOption.CASCADE)
    val catchphrase = varchar("catchphrase", 200)
    val description = varchar("description", 2000)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    override val primaryKey = PrimaryKey(storeId)
}

// Freeプランの生涯1回のAI生成回数はアカウント単位（owner_sub）でカウントする
// （店舗を削除・再作成しても引き継がれる。store_id単位にしない）。
object AiGenerationUsageTable : Table("ai_generation_usage") {
    val ownerSub = varchar("owner_sub", 255)
    val generationCount = integer("generation_count").default(0)

    override val primaryKey = PrimaryKey(ownerSub)
}

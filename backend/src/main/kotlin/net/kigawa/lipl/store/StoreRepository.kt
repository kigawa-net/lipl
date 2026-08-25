package net.kigawa.lipl.store

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom

private val slugRandom = SecureRandom()
private const val SLUG_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

fun generateSlug(): String =
    (1..8).map { SLUG_ALPHABET[slugRandom.nextInt(SLUG_ALPHABET.length)] }.joinToString("")

class StoreRepository {

    fun create(ownerSub: String, request: CreateStoreRequest): StoreResponse = transaction {
        val operationType = request.operationType ?: defaultOperationTypeFor(request.businessCategory)
        val slug = generateUniqueSlug()

        val storeId = StoresTable.insert {
            it[StoresTable.ownerSub] = ownerSub
            it[StoresTable.slug] = slug
            it[StoresTable.name] = request.name
            it[StoresTable.businessCategory] = request.businessCategory.name
            it[StoresTable.operationType] = operationType.name
            it[StoresTable.address] = request.address
            it[StoresTable.businessArea] = request.businessArea
            it[StoresTable.businessHours] = request.businessHours
            it[StoresTable.phone] = request.phone
        } get StoresTable.id

        request.snsLinks.forEach { link ->
            StoreSnsLinksTable.insert {
                it[StoreSnsLinksTable.storeId] = storeId
                it[StoreSnsLinksTable.platform] = link.platform.name
                it[StoreSnsLinksTable.url] = link.url
            }
        }

        toResponse(storeId)
    }

    fun listByOwner(ownerSub: String): List<StoreResponse> = transaction {
        StoresTable.selectAll()
            .andWhere { StoresTable.ownerSub eq ownerSub }
            .map { it[StoresTable.id] }
            .map { toResponse(it) }
    }

    fun isOwnedBy(storeId: Long, ownerSub: String): Boolean = transaction {
        StoresTable.selectAll()
            .andWhere { StoresTable.id eq storeId }
            .andWhere { StoresTable.ownerSub eq ownerSub }
            .any()
    }

    private fun generateUniqueSlug(): String {
        repeat(10) {
            val candidate = generateSlug()
            val exists = StoresTable.selectAll().andWhere { StoresTable.slug eq candidate }.any()
            if (!exists) return candidate
        }
        error("店舗slugの生成に失敗しました（衝突が続きました）")
    }

    private fun toResponse(storeId: Long): StoreResponse {
        val row = StoresTable.selectAll().andWhere { StoresTable.id eq storeId }.single()
        val links = StoreSnsLinksTable.selectAll()
            .andWhere { StoreSnsLinksTable.storeId eq storeId }
            .map { SnsLinkInput(SnsPlatform.valueOf(it[StoreSnsLinksTable.platform]), it[StoreSnsLinksTable.url]) }
        return row.toStoreResponse(links)
    }

    private fun ResultRow.toStoreResponse(snsLinks: List<SnsLinkInput>): StoreResponse = StoreResponse(
        id = this[StoresTable.id],
        slug = this[StoresTable.slug],
        name = this[StoresTable.name],
        businessCategory = BusinessCategory.valueOf(this[StoresTable.businessCategory]),
        operationType = OperationType.valueOf(this[StoresTable.operationType]),
        address = this[StoresTable.address],
        businessArea = this[StoresTable.businessArea],
        businessHours = this[StoresTable.businessHours],
        phone = this[StoresTable.phone],
        snsLinks = snsLinks,
    )
}

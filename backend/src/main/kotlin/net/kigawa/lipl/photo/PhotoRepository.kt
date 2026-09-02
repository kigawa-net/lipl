package net.kigawa.lipl.photo

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

class PhotoLimitExceededException(limit: Int) :
    Exception("写真は$limit 枚まで登録できます")

class PhotoNotFoundException : Exception("写真が見つかりません")

class PhotoRepository {

    fun listByStore(storeId: Long): List<PhotoResponse> = transaction {
        PhotosTable.selectAll()
            .andWhere { PhotosTable.storeId eq storeId }
            .orderBy(PhotosTable.displayOrder to SortOrder.ASC)
            .map { it.toResponse() }
    }

    fun create(storeId: Long, kaftUuid: String, filename: String): PhotoResponse = transaction {
        val currentCount = PhotosTable.selectAll().andWhere { PhotosTable.storeId eq storeId }.count()
        if (currentCount >= FREE_PLAN_PHOTO_LIMIT) {
            throw PhotoLimitExceededException(FREE_PLAN_PHOTO_LIMIT)
        }

        val nextOrder = (PhotosTable.selectAll().andWhere { PhotosTable.storeId eq storeId }
            .maxOfOrNull { it[PhotosTable.displayOrder] } ?: -1) + 1

        val id = PhotosTable.insert {
            it[PhotosTable.storeId] = storeId
            it[PhotosTable.kaftUuid] = kaftUuid
            it[PhotosTable.filename] = filename
            it[displayOrder] = nextOrder
        } get PhotosTable.id

        toResponse(id)
    }

    fun delete(storeId: Long, photoId: Long): String = transaction {
        val row = PhotosTable.selectAll()
            .andWhere { PhotosTable.id eq photoId }
            .andWhere { PhotosTable.storeId eq storeId }
            .singleOrNull() ?: throw PhotoNotFoundException()
        val kaftUuid = row[PhotosTable.kaftUuid]
        PhotosTable.deleteWhere { PhotosTable.id eq photoId }
        kaftUuid
    }

    // 店舗削除時に呼び出す。返り値のkaftUuid一覧は呼び出し側でkaftからも削除する。
    fun deleteByStore(storeId: Long): List<String> = transaction {
        val kaftUuids = PhotosTable.selectAll()
            .andWhere { PhotosTable.storeId eq storeId }
            .map { it[PhotosTable.kaftUuid] }
        PhotosTable.deleteWhere { PhotosTable.storeId eq storeId }
        kaftUuids
    }

    fun reorder(storeId: Long, orderedIds: List<Long>) = transaction {
        val existingIds = PhotosTable.selectAll().andWhere { PhotosTable.storeId eq storeId }
            .map { it[PhotosTable.id] }
            .toSet()
        if (existingIds != orderedIds.toSet()) {
            throw PhotoNotFoundException()
        }

        orderedIds.forEachIndexed { index, photoId ->
            PhotosTable.update({ PhotosTable.id eq photoId }) {
                it[displayOrder] = index
            }
        }
    }

    private fun toResponse(photoId: Long): PhotoResponse {
        val row = PhotosTable.selectAll().andWhere { PhotosTable.id eq photoId }.single()
        return row.toResponse()
    }

    private fun ResultRow.toResponse(): PhotoResponse = PhotoResponse(
        id = this[PhotosTable.id],
        storeId = this[PhotosTable.storeId],
        kaftUuid = this[PhotosTable.kaftUuid],
        filename = this[PhotosTable.filename],
        displayOrder = this[PhotosTable.displayOrder],
    )
}

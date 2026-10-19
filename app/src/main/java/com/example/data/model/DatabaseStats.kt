package com.example.data.model

data class ColumnInfo(
    val cid: Int,
    val name: String,
    val type: String,
    val notNull: Boolean,
    val defaultValue: String?,
    val primaryKey: Boolean
)

data class DatabaseStats(
    val dbPath: String,
    val fileSizeBytes: Long,
    val totalRecords: Int,
    val pageSize: Long,
    val pageCount: Long,
    val integrityCheck: String,
    val columns: List<ColumnInfo>
)

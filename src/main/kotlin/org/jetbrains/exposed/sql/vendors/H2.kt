package org.jetbrains.exposed.sql.vendors

import org.h2.engine.Session
import org.h2.jdbc.JdbcConnection
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.lang.UnsupportedOperationException

internal object H2Dialect: VendorDialect("h2") {

    // h2 supports only JDBC API from Java 1.6
    override fun getDatabase(): String {
        return TransactionManager.current().connection.catalog
    }

    override fun replace(table: Table, data: List<Pair<Column<*>, Any?>>, transaction: Transaction): String {
        if (currentMode() != "MySQL") throw UnsupportedOperationException("REPLACE is only supported in MySQL compatibility more for H2")

        val builder = QueryBuilder(true)
        val values = data.map { builder.registerArgument(it.first.columnType, it.second) }

        val inlineBuilder = QueryBuilder(false)
        val preparedValues = data.map { transaction.identity(it.first) to inlineBuilder.registerArgument(it.first.columnType, it.second) }


        return "INSERT INTO ${transaction.identity(table)} (${preparedValues.map { it.first }.joinToString()}) VALUES (${values.joinToString()}) ON DUPLICATE KEY UPDATE ${preparedValues.map { "${it.first}=${it.second}" }.joinToString()}"
    }

    override fun uuidType(): String = "UUID"

    private fun currentMode(): String {
        return ((TransactionManager.current().connection as? JdbcConnection)?.session as? Session)?.database?.mode?.name ?: ""
    }
}
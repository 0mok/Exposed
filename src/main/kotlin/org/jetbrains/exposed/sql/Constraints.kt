package org.jetbrains.exposed.sql

import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.vendors.currentDialect
import java.sql.DatabaseMetaData

interface DdlAware {
    fun createStatement(): List<String>
    fun modifyStatement(): List<String>
    fun dropStatement(): List<String>
}

enum class ReferenceOption {
    CASCADE,
    SET_NULL,
    RESTRICT; //default

    override fun toString(): String {
        return this.name.replace("_"," ")
    }

    companion object {
        fun resolveRefOptionFromJdbc(refOption: Int): ReferenceOption = when (refOption) {
            DatabaseMetaData.importedKeyCascade -> ReferenceOption.CASCADE
            DatabaseMetaData.importedKeySetNull -> ReferenceOption.SET_NULL
            DatabaseMetaData.importedKeyRestrict -> ReferenceOption.RESTRICT
            else -> ReferenceOption.RESTRICT
        }
    }
}

data class ForeignKeyConstraint(val fkName: String, val refereeTable: String, val refereeColumn:String,
                           val referencedTable: String, val referencedColumn: String, var deleteRule: ReferenceOption?) : DdlAware {

    companion object {
        fun from(column: Column<*>): ForeignKeyConstraint {
            assert(column.referee !== null) { "$column does not reference anything" }
            val s = TransactionManager.current()
            return ForeignKeyConstraint("", s.identity(column.referee!!.table), s.identity(column.referee!!), s.identity(column.table), s.identity(column), column.onDelete)
        }
    }

    override fun createStatement() = listOf(buildString{
        append("ALTER TABLE $referencedTable ADD")
        if (fkName.isNotBlank()) append(" CONSTRAINT $fkName")
        append(" FOREIGN KEY ($referencedColumn) REFERENCES $refereeTable($refereeColumn)")

        deleteRule?.let { onDelete ->
            append(" ON DELETE $onDelete")
        }
    })

    override fun dropStatement() = listOf("ALTER TABLE $refereeTable DROP FOREIGN KEY $fkName")

    override fun modifyStatement() = dropStatement() + createStatement()

}

data class Index(val indexName: String, val tableName: String, val columns: List<String>, val unique: Boolean) : DdlAware {
    companion object {
        fun forColumns(vararg columns: Column<*>, unique: Boolean): Index {
            assert(columns.isNotEmpty())
            assert(columns.groupBy { it.table }.size == 1) { "Columns from different tables can't persist in one index" }
            val indexName = "${columns.first().table.tableName}_${columns.joinToString("_"){it.name}}" + (if (unique) "_unique" else "")
            return Index(indexName, columns.first().table.tableName, columns.map { it.name }, unique)
        }
    }

    override fun createStatement() = listOf(currentDialect.createIndex(unique, tableName, indexName, columns))
    override fun dropStatement() = listOf(currentDialect.dropIndex(tableName, indexName))


    override fun modifyStatement() = dropStatement() + createStatement()


    fun onlyNameDiffer(other: Index): Boolean {
        return indexName != other.indexName && columns == other.columns && unique == other.unique
    }

    override fun toString(): String {
        return "${if (unique) "Unique " else ""}Index '$indexName' for '$tableName' on columns ${columns.joinToString(", ")}"
    }
}

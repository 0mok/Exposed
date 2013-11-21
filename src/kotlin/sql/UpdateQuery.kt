package kotlin.sql

import java.sql.Connection
import java.util.LinkedHashMap

class UpdateQuery(val table: Table, val where: Op<Boolean>) {
    val values = LinkedHashMap<Column<*>, Any>()

    fun <T> set(column: Column<T>, value: T) {
        if (values containsKey column) {
            error("$column is already initialized")
        }
        values[column] = value
    }

    fun execute(session: Session) {
        if (!values.isEmpty()) {
            val builder = QueryBuilder(true)
            var sql = StringBuilder("UPDATE ${session.identity(table)}")
            var c = 0;
            sql.append(" SET ")
            for ((col, value) in values) {
                sql.append(session.identity(col))
                   .append("=")
                   .append(builder.registerArgument(value, col.columnType))

                c++
                if (c < values.size()) {
                    sql.append(", ")
                }
            }
            sql.append(" WHERE " + where.toSQL(builder))
            log(sql)

            val statement = sql.toString()
            builder.executeUpdate(session, statement)
        }
    }
}

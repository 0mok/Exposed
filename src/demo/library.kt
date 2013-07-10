package demo

import com.sun.tools.javac.util.Name.Table
import java.util.Properties
import java.sql.DriverManager
import java.sql.Connection
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet

class Database(val url: String, val driver: String) {
    {
        Class.forName(driver)
    }

    fun withSession(statement: (session: Session) -> Unit) {
        val connection = DriverManager.getConnection(url)
        connection.setAutoCommit(false)
        statement(Session(connection))
        connection.commit()
        connection.close()
    }
}

class Session (val connection: Connection){
}

open class Table(name: String = "") {
    val tableName = if (name.length() > 0) name else this.javaClass.getSimpleName()

    val tableColumns: List<Column<*>> = ArrayList<Column<*>>()
    val primaryKeys: List<Column<*>> = ArrayList<Column<*>>()
    val foreignKeys: List<ForeignKey> = ArrayList<ForeignKey>()

    fun <T> column(name: String, columnType: ColumnType): Column<T> {
        val column = Column<T>(this, name, columnType)
        if (columnType == ColumnType.PRIMARY_KEY) {
            (primaryKeys as ArrayList<Column<*>>).add(column)
        }
        (tableColumns as ArrayList<Column<*>>).add(column)
        return column
    }

    fun foreignKey(column: Column<Int>, table: Table): ForeignKey {
        val foreignKey = ForeignKey(this, column, table)
        (foreignKeys as ArrayList<ForeignKey>).add(foreignKey)
        return foreignKey
    }
}

class ForeignKey(val table:Table, val column:Column<*>, val referencedTable:Table) {

}

open class Op : Expression {
    fun and(op: Op): Op {
        return AndOp(this, op)
    }

    fun or(op: Op): Op {
        return OrOp(this, op)
    }
}

trait Expression {

}

class LiteralOp(val value: Any): Op() {
    fun toString():String {
        return if (value is String) "'" + value + "'" else value.toString()
    }
}

class EqualsOp(val expr1: Expression, val expr2: Expression): Op() {
    fun toString():String {
        val sb = StringBuilder()
        if (expr1 is OrOp) {
            sb.append("(").append(expr1).append(")")
        } else {
            sb.append(expr1)
        }
        sb.append(" = ")
        if (expr2 is OrOp) {
            sb.append("(").append(expr2).append(")")
        } else {
            sb.append(expr2)
        }
        return sb.toString()
    }
}

class AndOp(val expr1: Expression, val expr2: Expression): Op() {
    fun toString():String {
        val sb = StringBuilder()
        if (expr1 is OrOp) {
            sb.append("(").append(expr1).append(")")
        } else {
            sb.append(expr1)
        }
        sb.append(" and ")
        if (expr2 is OrOp) {
            sb.append("(").append(expr2).append(")")
        } else {
            sb.append(expr2)
        }
        return sb.toString()
    }
}

class OrOp(val expr1: Expression, val expr2: Expression): Op() {
    fun toString():String {
        return expr1.toString() + " or " + expr2.toString()
    }
}

enum class ColumnType {
    PRIMARY_KEY
    INT
    STRING
}

class Column<T>(val table: Table, val name: String, val columnType: ColumnType) : Expression {
    fun equals(other: Expression): Op {
        return EqualsOp(this, other)
    }

    fun equals(other: Any): Op {
        return EqualsOp(this, LiteralOp(other))
    }

    fun toString(): String {
        return table.tableName + "." + name;
    }
}

fun Session.insert(vararg columns: Pair<Column<*>, Any>) {
    if (columns.size > 0) {
        val table = columns[0].component1().table
        var sql = StringBuilder("INSERT INTO ${table.tableName}")
        var c = 0
        sql.append(" (")
        for (column in columns) {
            sql.append(column.component1().name)
            c++
            if (c < columns.size) {
                sql.append(", ")
            }
        }
        sql.append(") ")
        c = 0
        sql.append("VALUES (")
        for (column in columns) {
            when (column.component1().columnType) {
                ColumnType.STRING -> sql.append("'" + column.component2() + "'")
                else -> sql.append(column.component2())
            }
            c++
            if (c < columns.size) {
                sql.append(", ")
            }
        }
        sql.append(") ")
        println("SQL: " + sql.toString())
        connection.createStatement()?.executeUpdate(sql.toString())
    }
}

fun Session.select(vararg columns: Column<*>): Query {
    return Query(connection, columns)
}

open class Query(val connection: Connection, val columns: Array<Column<*>>) {
    var op: Op? = null;
    var joinedTables = HashSet<Table>();
    var joins = HashSet<ForeignKey>();

    fun join (vararg foreignKeys: ForeignKey):Query {
        for (foreignKey in foreignKeys) {
            joins.add(foreignKey)
            joinedTables.add(foreignKey.referencedTable)
        }
        return this
    }

    fun where(op: Op): Query {
        this.op = op
        return this
    }

    fun groupBy(vararg b: Column<*>): Query {
        return this
    }

    fun forEach(statement: (row: Row) -> Unit) {
        val tables: MutableSet<Table> = HashSet<Table>()
        val sql = StringBuilder("SELECT ")
        if (columns.size > 0) {
            var c = 0;
            for (column in columns) {
                if (!joinedTables.contains(column.table)) {
                    tables.add(column.table)
                }
                sql.append(column.table.tableName).append(".").append(column.name)
                c++
                if (c < columns.size) {
                    sql.append(", ")
                }
            }
        }
        sql.append(" FROM ")
        var c= 0;
        for (table in tables) {
            sql.append(table.tableName)
            c++
            if (c < tables.size) {
                sql.append(", ")
            }
        }
        if (joins.size > 0) {
            for (foreignKey in joins) {
                sql.append(" JOIN ").append(foreignKey.referencedTable.tableName).append(" ON ").
                        append(foreignKey.referencedTable.primaryKeys[0]).append(" = ").append(foreignKey.column);
            }
        }
        if (op != null) {
            sql.append(" WHERE " + op.toString())
        }
        println("SQL: " + sql.toString())
        val rs = connection.createStatement()?.executeQuery(sql.toString())!!
        val values = HashMap<Column<*>, Any?>()
        while (rs.next()) {
            var c = 0;
            for (column in columns) {
                c++;
                values[column] = rs.getObject(c)
            }
            statement(Row(values))
        }
    }
}

class Row(val values: Map<Column<*>, *>) {
    fun <T> get(column: Column<T>): T {
        return values.get(column) as T
    }
}

fun Session.create(vararg tables: Table) {
    if (tables.size > 0) {
        for (table in tables) {
            var ddl = StringBuilder("CREATE TABLE ${table.tableName}")
            if (table.tableColumns.size > 0) {
                ddl.append(" (")
                var c = 0;
                for (column in table.tableColumns) {
                    ddl.append(column.name).append(" ")
                    when (column.columnType) {
                        ColumnType.PRIMARY_KEY -> ddl.append("INT PRIMARY KEY")
                        ColumnType.INT -> ddl.append("INT")
                        ColumnType.STRING -> ddl.append("VARCHAR(50)")
                        else -> throw IllegalStateException()
                    }
                    c++
                    if (c < table.tableColumns.size) {
                        ddl.append(", ")
                    }
                }
                ddl.append(");")
            }
            if (table.foreignKeys.size > 0) {
                for (foreignKey in table.foreignKeys) {
                    ddl.append(" ALTER TABLE ${table.tableName} ADD FOREIGN KEY (").append(foreignKey.column.name).
                            append(") REFERENCES ").append(foreignKey.column.table.tableName).append("(").
                            append(foreignKey.column.table.primaryKeys[0].name).append(");")
                }
            }
            println("SQL: " + ddl.toString())
            connection.createStatement()?.executeUpdate(ddl.toString())
        }
    }
}
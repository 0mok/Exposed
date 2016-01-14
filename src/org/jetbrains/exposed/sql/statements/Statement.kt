package org.jetbrains.exposed.sql.statements

import org.jetbrains.exposed.sql.*
import java.sql.PreparedStatement
import java.util.*

abstract class Statement<T>(val type: StatementType, val targets: List<Table>) {

    abstract fun PreparedStatement.executeInternal(transaction: Transaction): T?

    abstract fun prepareSQL(transaction: Transaction): String

    abstract fun arguments(): Iterable<Iterable<Pair<ColumnType, Any?>>>

    fun execute(transaction: Transaction): Pair<T?, List<StatementContext>> {
        try {
            transaction.monitor.register(transaction.logger)

            val sql = prepareSQL(transaction)
            val autoInc = if (type == StatementType.INSERT) targets.first().columns.filter { it.columnType.autoinc } else null
            val statement = transaction.prepareStatement(sql, autoInc?.map { transaction.identity(it)})

            val arguments = arguments()
            val contexts = if (arguments.count() > 0) {
                arguments.map { args ->
                    val context = StatementContext(sql, this, args)
                    transaction.monitor.notifyBeforeExecution(transaction, context)
                    statement.fillParameters(args)
                    statement.addBatch()
                    context
                }
            } else {
                val context = StatementContext(sql, this, emptyList())
                transaction.monitor.notifyBeforeExecution(transaction, context)
                listOf(context)
            }

            val result = statement.executeInternal(transaction)
            transaction.monitor.notifyAfterExecution(transaction, contexts, statement)
            return result to contexts
        } finally {
            transaction.monitor.unregister(transaction.logger)
        }
    }
}

class StatementContext(val sql: String, val statement: Statement<*>, val args: Iterable<Pair<ColumnType, Any?>>)

fun StatementContext.expandArgs() : String {
    val iterator = args.iterator()
    if (!iterator.hasNext())
        return sql

    return buildString {
        val quoteStack = Stack<Char>()
        var lastPos = 0
        for (i in 0..sql.length - 1) {
            val char = sql[i]
            if (char == '?') {
                if (quoteStack.isEmpty()) {
                    append(sql.substring(lastPos, i))
                    lastPos = i + 1
                    val (col, value) = iterator.next()
                    append(col.valueToString(value))
                }
                continue
            }

            if (char == '\'' || char == '\"') {
                if (quoteStack.isEmpty()) {
                    quoteStack.push(char)
                } else {
                    val currentQuote = quoteStack.peek()
                    if (currentQuote == char)
                        quoteStack.pop()
                    else
                        quoteStack.push(char)
                }
            }
        }

        if (lastPos < sql.length)
            append(sql.substring(lastPos))
    }
}

enum class StatementGroup {
    DDL, DML
}

enum class StatementType(val group: StatementGroup) {
    INSERT(StatementGroup.DML), UPDATE(StatementGroup.DML), DELETE(StatementGroup.DML), SELECT(StatementGroup.DML),
    CREATE(StatementGroup.DDL), ALTER(StatementGroup.DDL), TRUNCATE(StatementGroup.DDL), DROP(StatementGroup.DDL)
}
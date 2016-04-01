package org.jetbrains.exposed.sql.tests

import com.mysql.management.MysqldResource
import com.mysql.management.driverlaunched.MysqldResourceNotFoundException
import com.mysql.management.driverlaunched.ServerLauncherSocketFactory
import com.mysql.management.util.Files
import de.flapdoodle.embed.process.distribution.Platform
import org.jetbrains.exposed.dao.EntityCache
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.exposedLogger
import org.jetbrains.exposed.sql.vendors.*
import org.joda.time.DateTimeZone
import ru.yandex.qatools.embed.postgresql.PgSQLServiceUtils
import ru.yandex.qatools.embed.postgresql.PostgresStarter
import ru.yandex.qatools.embed.postgresql.config.AbstractPostgresConfig
import ru.yandex.qatools.embed.postgresql.config.PostgresConfig
import ru.yandex.qatools.embed.postgresql.distribution.Version
import java.util.*
import kotlin.concurrent.thread

enum class TestDB(val dialect: DatabaseDialect, val connection: String, val driver: String, val beforeConnection: () -> Any, val afterConnection: () -> Unit) {
    H2(H2Dialect, "jdbc:h2:mem:", "org.h2.Driver", {Unit}, {}),
    MYSQL(MysqlDialect, "jdbc:mysql:mxj://localhost:12345/testdb1?createDatabaseIfNotExist=true&server.initialize-user=false&user=root&password=", "com.mysql.jdbc.Driver",
            beforeConnection = { System.setProperty(Files.USE_TEST_DIR, java.lang.Boolean.TRUE!!.toString()); Files().cleanTestDir(); Unit },
            afterConnection = {
                try {
                    val baseDir = Files().tmp(MysqldResource.MYSQL_C_MXJ)
                    ServerLauncherSocketFactory.shutdown(baseDir, null)
                } catch (e: MysqldResourceNotFoundException) {
                    exposedLogger.warn(e.message, e)
                } finally {
                    Files().cleanTestDir()
                }
            }),
    POSTGRESQL(PostgreSQLDialect, "jdbc:postgresql://localhost:12346/template1?user=root&password=root", "org.postgresql.Driver",
            beforeConnection = { postgresSQLProcess.start() }, afterConnection = { postgresSQLProcess.stop() });

    companion object {
        fun enabledInTests(): List<TestDB> {
            val concreteDialects = System.getProperty("exposed.test.dialects", "").let {
                if (it == "") return emptyList()
                else it.split(',').map { it.trim().toUpperCase() }
            }
            return values().filter { concreteDialects.isEmpty() || it.name in concreteDialects }
        }
    }
}

private val registeredOnShutdown = HashSet<TestDB>()

private val postgresSQLProcess by lazy {
    val config = PostgresConfig(Version.Main.PRODUCTION, AbstractPostgresConfig.Net("localhost", 12346),
            AbstractPostgresConfig.Storage("template1"), AbstractPostgresConfig.Timeout(),
            AbstractPostgresConfig.Credentials("root", "root"));


    if (Platform.detect() == Platform.Windows) {
        PgSQLServiceUtils.initDB(config)
    } else {
        PostgresStarter.getDefaultInstance().prepare(config)
    }
}

abstract class DatabaseTestsBase() {
    fun withDb(dbSettings: TestDB, statement: Transaction.() -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        DateTimeZone.setDefault(DateTimeZone.UTC)

        if (dbSettings !in registeredOnShutdown) {
            dbSettings.beforeConnection()
            Runtime.getRuntime().addShutdownHook(thread(false ){ dbSettings.afterConnection() })
            registeredOnShutdown += dbSettings
        }

        var db = Database.connect(dbSettings.connection, user = "root", driver = dbSettings.driver)

        db.transaction {
            statement()
        }
    }

    fun withDB(statement: Transaction.() -> Unit) {
        TestDB.enabledInTests().forEach {
            withDb(it, statement)
        }
    }

    fun withTables (excludeSettings: List<TestDB>, vararg tables: Table, statement: Transaction.() -> Unit) {
        (TestDB.enabledInTests().toList() - excludeSettings).forEach {
            withDb(it) {
                create(*tables)
                try {
                    statement()
                    commit() // Need commit to persist data before drop tables
                } finally {
                    drop (*EntityCache.sortTablesByReferences(tables.toList()).reversed().toTypedArray())
                }
            }
        }
    }
    fun withTables (vararg tables: Table, statement: Transaction.() -> Unit)
            = withTables(excludeSettings = emptyList(), tables = *tables, statement = statement)

    fun Transaction.assertEquals(a: Any?, b: Any?) = kotlin.test.assertEquals(a, b, "Failed on ${currentDialect.name}")
}

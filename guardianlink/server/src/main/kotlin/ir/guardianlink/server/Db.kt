package ir.guardianlink.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import javax.sql.DataSource

class Db(val cfg: AppConfig) : AutoCloseable {
    val ds: DataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = cfg.dbUrl
            username = cfg.dbUser
            password = cfg.dbPass
            maximumPoolSize = cfg.dbPoolMaxSize
            minimumIdle = cfg.dbPoolMinIdle
            connectionTimeout = cfg.dbConnectionTimeoutMs
            validationTimeout = cfg.dbValidationTimeoutMs
            idleTimeout = cfg.dbIdleTimeoutMs
            maxLifetime = cfg.dbMaxLifetimeMs
            poolName = "guardianlink-db-pool"
            addDataSourceProperty("connectTimeout", "5")
            addDataSourceProperty("socketTimeout", "30")
            addDataSourceProperty("tcpKeepAlive", "true")
        }
    )

    fun migrate() {
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    fun <T> tx(body: (Connection) -> T): T {
        val c = ds.connection
        return try {
            c.autoCommit = false
            val r = body(c)
            c.commit()
            r
        } catch (e: Exception) {
            c.rollback()
            throw e
        } finally {
            c.autoCommit = true
            c.close()
        }
    }

    fun <T> query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
        ds.connection.use { c -> c.query(sql, *params, map = map) }

    fun <T> one(sql: String, vararg params: Any?, map: (ResultSet) -> T): T? =
        query(sql, *params, map = map).firstOrNull()

    fun exec(sql: String, vararg params: Any?): Int =
        ds.connection.use { c -> c.exec(sql, *params) }

    override fun close() {
        (ds as HikariDataSource).close()
    }
}

fun bind(p: PreparedStatement, params: Array<out Any?>) {
    params.forEachIndexed { i, v ->
        val idx = i + 1
        when (v) {
            null -> p.setObject(idx, null)
            is String -> p.setString(idx, v)
            is Int -> p.setInt(idx, v)
            is Long -> p.setLong(idx, v)
            is Boolean -> p.setBoolean(idx, v)
            is UUID -> p.setObject(idx, v)
            is OffsetDateTime -> p.setObject(idx, v)
            else -> p.setObject(idx, v)
        }
    }
}

fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
    measureDbStatement {
        prepareStatement(sql).use { p ->
            p.queryTimeout = 30
            bind(p, params)
            p.executeQuery().use { rs ->
                val out = ArrayList<T>()
                while (rs.next()) out.add(map(rs))
                out
            }
        }
    }

fun <T> Connection.one(sql: String, vararg params: Any?, map: (ResultSet) -> T): T? =
    query(sql, *params, map = map).firstOrNull()

fun Connection.exec(sql: String, vararg params: Any?): Int = measureDbStatement {
    prepareStatement(sql).use { p ->
        p.queryTimeout = 30
        bind(p, params)
        p.executeUpdate()
    }
}

fun Connection.execReturningUuid(sql: String, vararg params: Any?): UUID = measureDbStatement {
    // SQL call sites already contain RETURNING id; do not also request generated columns via JDBC.
    prepareStatement(sql).use { p ->
        p.queryTimeout = 30
        bind(p, params)
        p.executeQuery().use { rs -> if (rs.next()) rs.getObject(1, UUID::class.java) else error("no id returned") }
    }
}

fun ResultSet.uuid(i: String): UUID = getObject(i, UUID::class.java)
fun ResultSet.uuidN(i: String): UUID? = getObject(i, UUID::class.java)
fun ResultSet.odt(i: String): OffsetDateTime = getObject(i, OffsetDateTime::class.java)
fun ResultSet.odtN(i: String): OffsetDateTime? = getObject(i, OffsetDateTime::class.java)
fun ResultSet.intN(i: String): Int? = getObject(i) as Int?

package net.kigawa.lipl.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import javax.sql.DataSource

data class DbConfig(
    val host: String,
    val port: Int,
    val name: String,
    val user: String,
    val password: String,
) {
    val jdbcUrl: String = "jdbc:mariadb://$host:$port/$name"
}

fun dbConfigFromEnv(): DbConfig = DbConfig(
    host = System.getenv("DB_HOST") ?: error("環境変数 DB_HOST が設定されていません"),
    port = (System.getenv("DB_PORT") ?: "3306").toInt(),
    name = System.getenv("DB_NAME") ?: error("環境変数 DB_NAME が設定されていません"),
    user = System.getenv("DB_USER") ?: error("環境変数 DB_USER が設定されていません"),
    password = System.getenv("DB_PASSWORD") ?: error("環境変数 DB_PASSWORD が設定されていません"),
)

fun createDataSource(config: DbConfig): HikariDataSource {
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = config.jdbcUrl
        username = config.user
        password = config.password
        driverClassName = "org.mariadb.jdbc.Driver"
        maximumPoolSize = 10
    }
    return HikariDataSource(hikariConfig)
}

fun migrate(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()
        .migrate()
}

fun connectDatabase(dataSource: DataSource): Database = Database.connect(dataSource)

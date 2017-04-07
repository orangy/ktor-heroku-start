package org.jetbrains.ktor.heroku

import com.zaxxer.hikari.*
import freemarker.cache.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.freemarker.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*
import java.util.*

val hikariConfig = HikariConfig().apply {
    jdbcUrl = System.getenv("JDBC_DATABASE_URL")
}

val dataSource = if (hikariConfig.jdbcUrl != null)
    HikariDataSource(hikariConfig)
else
    HikariDataSource()

val html_utf8 = ContentType.Text.Html.withCharset(Charsets.UTF_8)

fun Application.module() {
    install(DefaultHeaders)
    install(ConditionalHeaders)
    install(PartialContentSupport)

    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(environment.classLoader, "templates")
    }

    install(StatusPages) {
        exception<Exception> { exception ->
            call.respond(FreeMarkerContent("error.ftl", exception, "", html_utf8))
        }
    }

    install(Routing) {
        serveClasspathResources("public")

        get("hello") {
            call.respond("Hello World")
        }

        get("error") {
            throw IllegalStateException("An invalid place to be …")
        }

        get("/") {
            val model = HashMap<String, Any>()
            model.put("message", "Hello World!")
            val etag = model.toString().hashCode().toString()
            call.respond(FreeMarkerContent("index.ftl", model, etag, html_utf8))
        }

        get("/db") {
            val model = HashMap<String, Any>()
            dataSource.connection.use { connection ->
                val rs = connection.createStatement().run {
                    executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp)")
                    executeUpdate("INSERT INTO ticks VALUES (now())")
                    executeQuery("SELECT tick FROM ticks")
                }

                val output = ArrayList<String>()
                while (rs.next()) {
                    output.add("Read from DB: " + rs.getTimestamp("tick"))
                }
                model.put("results", output)
            }

            val etag = model.toString().hashCode().toString()
            call.respond(FreeMarkerContent("db.ftl", model, etag, html_utf8))
        }
    }
}

fun main(args: Array<String>) {
    val port = Integer.valueOf(System.getenv("PORT"))
    embeddedServer(Netty, port, reloadPackages = listOf("heroku"), module = Application::module).start()
}



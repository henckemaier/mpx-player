package com.mpx.desktop

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.LocalFileContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * Modulo Ktor do agente: /ping //library //stream(Range) //art, TODOS atras de [token]
 * (Bearer OU ?token=; ausente/errado -> 401). PartialContent do Ktor trata Range/206 (nao
 * implementamos Range na mao). Funcao separada do Main p/ o ktor-server-test-host exercitar
 * auth e Range sem subir socket/PC real.
 */
fun Application.mpxAgentModule(library: Library, token: String, version: String) {
    install(ContentNegotiation) { json() }
    install(PartialContent)
    install(StatusPages) {
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respondText("401 Unauthorized\n", status = status)
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respondText("404 Not Found\n", status = status)
        }
        exception<Throwable> { call, cause ->
            call.respondText("500 ${cause.message}\n", status = HttpStatusCode.InternalServerError)
        }
    }

    // Gate de token ANTES do routing: Authorization: Bearer <token> OU ?token=<token>.
    intercept(ApplicationCallPipeline.Plugins) {
        val auth = call.request.header(HttpHeaders.Authorization)
        val bearer = auth?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substring(7)?.trim()
        val provided = bearer ?: call.request.queryParameters["token"]
        if (provided == null || provided != token) {
            call.respond(HttpStatusCode.Unauthorized)
            return@intercept finish()
        }
    }

    routing {
        get("/ping") {
            call.respond(PingDto(name = "MPX Desktop Agent", version = version, trackCount = library.count))
        }
        get("/library") {
            call.respond(library.dtos)
        }
        get("/stream/{id}") {
            val id = call.parameters["id"].orEmpty()
            val file = library.file(id)
            if (file == null || !file.isFile) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            val mime = library.mime(id) ?: "application/octet-stream"
            // LocalFileContent + PartialContent -> Accept-Ranges: bytes e 206 quando houver Range.
            call.respond(LocalFileContent(file, contentType = ContentType.parse(mime)))
        }
        get("/art/{id}") {
            val id = call.parameters["id"].orEmpty()
            val art = library.art(id)
            if (art == null) {
                call.respond(HttpStatusCode.NotFound)
                return@get
            }
            call.respondBytes(art.bytes, ContentType.parse(art.contentType))
        }
    }
}

package com.mpx.desktop

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** (d) auth: sem token -> 401; com token (Bearer OU ?token=) -> 200. */
class ServerAuthTest {

    private val token = "secret-token-xyz"
    private val library = Library(emptyList())

    private fun io.ktor.server.testing.ApplicationTestBuilder.installAgent() {
        application { mpxAgentModule(library, token, "test") }
    }

    @Test
    fun semToken_401() = testApplication {
        installAgent()
        val r = client.get("/ping")
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun tokenErrado_401() = testApplication {
        installAgent()
        val r = client.get("/ping") { header(HttpHeaders.Authorization, "Bearer nope") }
        assertEquals(HttpStatusCode.Unauthorized, r.status)
    }

    @Test
    fun bearerCerto_200() = testApplication {
        installAgent()
        val r = client.get("/ping") { header(HttpHeaders.Authorization, "Bearer $token") }
        assertEquals(HttpStatusCode.OK, r.status)
        val body = r.bodyAsText()
        assertTrue("MPX Desktop Agent" in body, "ping deve trazer o nome do agente")
    }

    @Test
    fun queryTokenCerto_200() = testApplication {
        installAgent()
        val r = client.get("/library?token=$token")
        assertEquals(HttpStatusCode.OK, r.status)
    }

    @Test
    fun libraryProtegida_semToken_401() = testApplication {
        installAgent()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/library").status)
    }
}

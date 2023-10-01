package io.lb

import io.ktor.server.testing.testApplication
import org.junit.Test

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        application {
        }
    }
}

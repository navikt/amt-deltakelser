import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*
import sharedui.simNavHeader
import sharedui.simNavHeaderStyles

fun Route.simNavHomeRoutes() {
    get("/") {
        call.respondHtml {
            simNavHomePage()
        }
    }
}

private fun HTML.simNavHomePage() {
    head {
        title("Sim-nav")
        simNavHeaderStyles()
        style {
            unsafe {
                +"""
                body { font-family: sans-serif; margin: 2rem; }
                .intro { margin-bottom: 1rem; }
                """.trimIndent()
            }
        }
    }

    body {
        simNavHeader("/")
        h1 { +"Sim-nav" }
        p(classes = "intro") { +"Choose a feature from the menu above." }
    }
}


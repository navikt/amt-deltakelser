import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import sharedui.simNavFormPageStyles
import sharedui.simNavHeader
import sharedui.simNavHeaderStyles
import tjenester.auth.FrontendAuthState
import tjenester.nav.pdl.PdlDataSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val INNBYGGERS_FLATE_LAUNCHER_PATH = "/innbyggers-flate"
private const val INNBYGGERS_FLATE_URL = "http://127.0.0.1:3005"
private const val INNBYGGERS_FLATE_FRONTEND_AUTH_PATH = "$INNBYGGERS_FLATE_LAUNCHER_PATH/frontend-auth"

private const val STATIC_DELTAKER_ID = "00000000-0000-0000-0000-000000000001"

fun Route.innbyggersFlateLauncherRoutes(
    pdlDataSource: PdlDataSource,
) {
    get(INNBYGGERS_FLATE_LAUNCHER_PATH) {
        val options = loadInnbyggersFlateOptions(pdlDataSource)
        call.respondHtml {
            innbyggersFlateLauncherPage(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
                personOptions = options.persons,
                currentFrontendPid = options.currentFrontendPid,
                currentFrontendPidLabel = options.currentFrontendPidLabel,
            )
        }
    }

    post(INNBYGGERS_FLATE_FRONTEND_AUTH_PATH) {
        val submittedPid = call.receiveParameters()["pid"]?.trim().orEmpty()
        val validPids = loadInnbyggersFlateOptions(pdlDataSource).persons.associate { it.value to it.label }

        if (submittedPid.isBlank()) {
            call.redirectToInnbyggersFlateLauncher("Velg en person", isError = true)
            return@post
        }

        if (!validPids.containsKey(submittedPid)) {
            call.redirectToInnbyggersFlateLauncher("Ukjent pid: $submittedPid", isError = true)
            return@post
        }

        FrontendAuthState.updatePid(submittedPid)
        call.redirectToInnbyggersFlateLauncher("Oppdatert frontend-pid til ${validPids[submittedPid]}")
    }
}

private fun loadInnbyggersFlateOptions(pdlDataSource: PdlDataSource): InnbyggersFlateOptions {
    val persons = pdlDataSource.allPersons()
        .entries
        .sortedBy { (fnr, person) ->
            person.navn.firstOrNull()?.let { "${it.etternavn} ${it.fornavn}" } ?: fnr
        }
        .map { (fnr, person) ->
            val name = person.navn.firstOrNull()?.let { navn ->
                listOfNotNull(navn.fornavn, navn.mellomnavn, navn.etternavn).joinToString(" ")
            } ?: fnr
            PersonSelectOption(
                value = fnr,
                label = "$name - $fnr",
            )
        }

    val currentFrontendPid = FrontendAuthState.getPid()
    val currentFrontendPidLabel = currentFrontendPid?.let { pid ->
        persons.firstOrNull { it.value == pid }?.label ?: pid
    } ?: "Ikke satt enda"

    return InnbyggersFlateOptions(
        persons = persons,
        currentFrontendPid = currentFrontendPid,
        currentFrontendPidLabel = currentFrontendPidLabel,
    )
}

private fun HTML.innbyggersFlateLauncherPage(
    message: String?,
    isError: Boolean,
    personOptions: List<PersonSelectOption>,
    currentFrontendPid: String?,
    currentFrontendPidLabel: String,
) {
    head {
        title("Start innbyggers-flate")
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        simNavHeaderStyles()
        simNavFormPageStyles(fieldSelector = "select")
        style {
            unsafe {
                +"""
                .message { padding: 0.75rem; border-radius: 6px; margin-bottom: 1rem; }
                .message--ok { background: #ebfbee; border: 1px solid #b2f2bb; }
                .message--error { background: #fff5f5; border: 1px solid #ffc9c9; }
                .frontend-auth-panel { border: 1px solid #d8d8d8; border-radius: 6px; padding: 1rem; margin-bottom: 1rem; background: #fafafa; }
                .frontend-auth-panel__current { font-weight: 600; margin-bottom: 0.75rem; }
                .frontend-auth-panel__hint { margin-bottom: 0.75rem; color: #595959; }
                .frontend-auth-panel form { border: 0; padding: 0; border-radius: 0; background: transparent; }
                .static-value { font-family: monospace; background: #f3f4f6; padding: 0.25rem 0.5rem; border-radius: 4px; border: 1px solid #d1d5db; }
                p { margin-top: 0; }
                """.trimIndent()
            }
        }
    }

    body {
        simNavHeader(INNBYGGERS_FLATE_LAUNCHER_PATH)
        main {
            h1 { +"Start innbyggers-flate" }
            p { +"Velg innbygger og åpne appen med ønsket deltaker." }

            if (message != null) {
                p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                    +message
                }
            }

            section(classes = "frontend-auth-panel") {
                h2 { +"Frontend pid (innbygger)" }
                p(classes = "frontend-auth-panel__current") {
                    +"Aktiv pid i frontend-token: $currentFrontendPidLabel"
                }
                if (currentFrontendPid == null) {
                    p(classes = "frontend-auth-panel__hint") {
                        +"Frontend-token kan ikke hentes for proxy før pid er valgt."
                    }
                }
                form(action = INNBYGGERS_FLATE_FRONTEND_AUTH_PATH, method = FormMethod.post) {
                    div("field") {
                        label {
                            htmlFor = "pid"
                            +"Innlogget innbygger"
                        }
                        select {
                            id = "pid"
                            name = "pid"
                            required = true
                            option {
                                value = ""
                                selected = currentFrontendPid == null
                                +"Velg person"
                            }
                            personOptions.forEach { option ->
                                option {
                                    value = option.value
                                    selected = option.value == currentFrontendPid
                                    +option.label
                                }
                            }
                        }
                    }
                    button(type = ButtonType.submit) { +"Oppdater pid" }
                }
            }

            form(action = "$INNBYGGERS_FLATE_URL/arbeidsmarkedstiltak/$STATIC_DELTAKER_ID", method = FormMethod.get) {
                target = "_blank"

                div("field") {
                    label { +"Deltaker-ID (statisk)" }
                    p {
                        span(classes = "static-value") { +STATIC_DELTAKER_ID }
                    }
                }

                button(type = ButtonType.submit) { +"Åpne innbyggers-flate" }
            }
        }
    }
}

private data class InnbyggersFlateOptions(
    val persons: List<PersonSelectOption>,
    val currentFrontendPid: String?,
    val currentFrontendPidLabel: String,
)

private data class PersonSelectOption(
    val value: String,
    val label: String,
)

private suspend fun io.ktor.server.application.ApplicationCall.redirectToInnbyggersFlateLauncher(
    message: String,
    isError: Boolean = false,
) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$INNBYGGERS_FLATE_LAUNCHER_PATH?message=$encodedMessage&isError=$isError")
}


import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import sharedui.*
import tjenester.auth.FrontendAuthState
import tjenester.nav.pdl.PdlDataSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val TILTAKSARRANGOR_FLATE_LAUNCHER_PATH = "/tiltaksarrangor-flate"
private const val TILTAKSARRANGOR_FLATE_URL = "http://localhost:3001/deltakeroversikt/"
private const val TILTAKSARRANGOR_FLATE_FRONTEND_AUTH_PATH = "$TILTAKSARRANGOR_FLATE_LAUNCHER_PATH/frontend-auth"

fun Route.tiltaksarrangorFlateLauncherRoutes(
    pdlDataSource: PdlDataSource,
) {
    get(TILTAKSARRANGOR_FLATE_LAUNCHER_PATH) {
        val options = loadTiltaksarrangorFlateOptions(pdlDataSource)
        call.respondHtml {
            tiltaksarrangorFlateLauncherPage(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
                personOptions = options.persons,
                currentFrontendPid = options.currentFrontendPid,
                currentFrontendPidLabel = options.currentFrontendPidLabel,
            )
        }
    }

    post(TILTAKSARRANGOR_FLATE_FRONTEND_AUTH_PATH) {
        val submittedPid = call.receiveParameters()["pid"]?.trim().orEmpty()
        val validPids = loadTiltaksarrangorFlateOptions(pdlDataSource).persons.associate { it.value to it.label }

        if (submittedPid.isBlank()) {
            call.redirectToTiltaksarrangorFlateLauncher("Velg en person", isError = true)
            return@post
        }

        if (!validPids.containsKey(submittedPid)) {
            call.redirectToTiltaksarrangorFlateLauncher("Ukjent pid: $submittedPid", isError = true)
            return@post
        }

        FrontendAuthState.updatePid(submittedPid)
        call.redirectToTiltaksarrangorFlateLauncher("Oppdatert frontend-pid til ${validPids[submittedPid]}")
    }
}

private fun loadTiltaksarrangorFlateOptions(
    pdlDataSource: PdlDataSource,
): TiltaksarrangorFlateOptions {
    val persons = pdlDataSource.allPersons()
        .entries
        .sortedBy { (fnr, person) ->
            person.navn.firstOrNull()?.let { "${it.etternavn} ${it.fornavn}" } ?: fnr
        }
        .map { (fnr, person) ->
            val name = person.navn.firstOrNull()?.let { navn ->
                listOfNotNull(navn.fornavn, navn.mellomnavn, navn.etternavn).joinToString(" ")
            } ?: fnr
            PidSelectOption(
                value = fnr,
                label = "$name - $fnr",
            )
        }

    val currentFrontendPid = FrontendAuthState.getPid()
    val currentFrontendPidLabel = currentFrontendPid?.let { pid ->
        persons.firstOrNull { it.value == pid }?.label ?: pid
    } ?: "Ikke satt enda"

    return TiltaksarrangorFlateOptions(
        persons = persons,
        currentFrontendPid = currentFrontendPid,
        currentFrontendPidLabel = currentFrontendPidLabel,
    )
}

private fun HTML.tiltaksarrangorFlateLauncherPage(
    message: String?,
    isError: Boolean,
    personOptions: List<PidSelectOption>,
    currentFrontendPid: String?,
    currentFrontendPidLabel: String,
) {
    head {
        title("Start tiltaksarrangor-flate")
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        simNavHeaderStyles()
        simNavFormPageStyles(fieldSelector = "select")
        simNavLauncherUiStyles()
    }

    body {
        simNavHeader(TILTAKSARRANGOR_FLATE_LAUNCHER_PATH)
        main {
            h1 { +"Start tiltaksarrangor-flate" }
            p { +"Velg innlogget person og åpne deltakeroversikten." }

            simNavLauncherMessage(message, isError)

            section(classes = "frontend-auth-panel") {
                h2 { +"Kontekst" }
                p(classes = "frontend-auth-panel__current") {
                    +"Aktiv pid i frontend-token: $currentFrontendPidLabel"
                }
                if (currentFrontendPid == null) {
                    p(classes = "frontend-auth-panel__hint") {
                        +"Frontend-token kan ikke hentes for proxy før pid er valgt."
                    }
                }
                form(action = TILTAKSARRANGOR_FLATE_FRONTEND_AUTH_PATH, method = FormMethod.post) {
                    div("field") {
                        label {
                            htmlFor = "pid"
                            +"Innlogget person"
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

            if (currentFrontendPid == null) {
                p { +"Velg person ovenfor for a aktivere oppstart av flaten." }
            } else {
                form(action = TILTAKSARRANGOR_FLATE_URL, method = FormMethod.get) {
                    target = "_blank"
                    button(type = ButtonType.submit) { +"Aapne tiltaksarrangor-flate" }
                }
            }
        }
    }
}

private data class TiltaksarrangorFlateOptions(
    val persons: List<PidSelectOption>,
    val currentFrontendPid: String?,
    val currentFrontendPidLabel: String,
)

private data class PidSelectOption(
    val value: String,
    val label: String,
)

private suspend fun io.ktor.server.application.ApplicationCall.redirectToTiltaksarrangorFlateLauncher(
    message: String,
    isError: Boolean = false,
) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$TILTAKSARRANGOR_FLATE_LAUNCHER_PATH?message=$encodedMessage&isError=$isError")
}


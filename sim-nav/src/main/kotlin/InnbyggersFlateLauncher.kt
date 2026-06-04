import db.AmtDeltakerRepository
import db.DeltakerOption
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import sharedui.simNavFormPageStyles
import sharedui.simNavHeader
import sharedui.simNavHeaderStyles
import sharedui.simNavLauncherMessage
import sharedui.simNavLauncherUiStyles
import tjenester.auth.FrontendAuthState
import tjenester.nav.pdl.PdlDataSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val INNBYGGERS_FLATE_LAUNCHER_PATH = "/innbyggers-flate"
private const val INNBYGGERS_FLATE_URL = "http://127.0.0.1:3005"
private const val INNBYGGERS_FLATE_FRONTEND_AUTH_PATH = "$INNBYGGERS_FLATE_LAUNCHER_PATH/frontend-auth"

fun Route.innbyggersFlateLauncherRoutes(
    pdlDataSource: PdlDataSource,
    amtDeltakerRepository: AmtDeltakerRepository,
) {
    get(INNBYGGERS_FLATE_LAUNCHER_PATH) {
        val options = loadInnbyggersFlateOptions(pdlDataSource, amtDeltakerRepository)
        call.respondHtml {
            innbyggersFlateLauncherPage(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
                personOptions = options.persons,
                currentFrontendPid = options.currentFrontendPid,
                currentFrontendPidLabel = options.currentFrontendPidLabel,
                deltakerOptions = options.deltakere,
            )
        }
    }

    post(INNBYGGERS_FLATE_FRONTEND_AUTH_PATH) {
        val submittedPid = call.receiveParameters()["pid"]?.trim().orEmpty()
        val validPids = loadInnbyggersFlateOptions(pdlDataSource, amtDeltakerRepository).persons.associate { it.value to it.label }

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

private fun loadInnbyggersFlateOptions(
    pdlDataSource: PdlDataSource,
    amtDeltakerRepository: AmtDeltakerRepository,
): InnbyggersFlateOptions {
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

    val deltakere = currentFrontendPid?.let { pid ->
        runCatching { amtDeltakerRepository.getDeltakereForPersonident(pid) }
            .onFailure { println("Failed to fetch deltakere for pid $pid: ${it.message}") }
            .getOrElse { emptyList() }
    } ?: emptyList()

    return InnbyggersFlateOptions(
        persons = persons,
        currentFrontendPid = currentFrontendPid,
        currentFrontendPidLabel = currentFrontendPidLabel,
        deltakere = deltakere,
    )
}

private fun HTML.innbyggersFlateLauncherPage(
    message: String?,
    isError: Boolean,
    personOptions: List<PersonSelectOption>,
    currentFrontendPid: String?,
    currentFrontendPidLabel: String,
    deltakerOptions: List<DeltakerOption>,
) {
    head {
        title("Start innbyggers-flate")
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        simNavHeaderStyles()
        simNavFormPageStyles(fieldSelector = "select")
        simNavLauncherUiStyles()
    }

    body {
        simNavHeader(INNBYGGERS_FLATE_LAUNCHER_PATH)
        main {
            h1 { +"Start innbyggers-flate" }
            p { +"Velg innbygger og åpne appen for ønsket deltakelse." }

            simNavLauncherMessage(message, isError)

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

            if (currentFrontendPid != null) {
                if (deltakerOptions.isEmpty()) {
                    p { +"Ingen deltakelser funnet for valgt innbygger i amt-deltaker." }
                } else {
                    form(method = FormMethod.get) {
                        id = "innbyggers-flate-form"
                        target = "_blank"

                        div("field") {
                            label {
                                htmlFor = "deltaker_id"
                                +"Deltakelse"
                            }
                            select {
                                id = "deltaker_id"
                                name = "deltaker_id"
                                required = true
                                deltakerOptions.forEachIndexed { index, option ->
                                    val statusLabel = option.status?.let { " [$it]" } ?: ""
                                    this@select.option {
                                        value = option.id.toString()
                                        selected = index == 0
                                        +"${option.id} - ${option.deltakerlisteNavn} – $statusLabel"
                                    }
                                }
                            }
                        }

                        button(type = ButtonType.submit) { +"Åpne innbyggers-flate" }
                    }

                    script {
                        unsafe {
                            +"""
                            (() => {
                              const form = document.getElementById('innbyggers-flate-form');
                              if (!form) return;
                              form.addEventListener('submit', (e) => {
                                e.preventDefault();
                                const deltakerId = document.getElementById('deltaker_id')?.value;
                                window.open('${INNBYGGERS_FLATE_URL}/arbeidsmarkedstiltak/' + encodeURIComponent(deltakerId), '_blank');
                              });
                            })();
                            """.trimIndent()
                        }
                    }
                }
            } else {
                p { +"Velg innbygger ovenfor for å se tilgjengelige deltakelser." }
            }
        }
    }
}

private data class InnbyggersFlateOptions(
    val persons: List<PersonSelectOption>,
    val currentFrontendPid: String?,
    val currentFrontendPidLabel: String,
    val deltakere: List<DeltakerOption>,
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

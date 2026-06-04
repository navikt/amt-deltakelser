import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import sharedui.*
import tjenester.auth.FrontendAuthState
import tjenester.nav.nom.fetchNomRessurser
import tjenester.nav.valp.fetchGjennomforinger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val TILTAKSKOORDINATOR_FLATE_LAUNCHER_PATH = "/tiltakskoordinator-flate"
private const val TILTAKSKOORDINATOR_FLATE_URL = "http://localhost:3003"
private const val TILTAKSKOORDINATOR_FLATE_KONTEKST_PATH = "$TILTAKSKOORDINATOR_FLATE_LAUNCHER_PATH/kontekst"
private const val TILTAKSKOORDINATOR_FLATE_OPEN_PATH = "$TILTAKSKOORDINATOR_FLATE_LAUNCHER_PATH/open"

fun Route.tiltaksKoordinatorFlateLauncherRoutes() {
    get(TILTAKSKOORDINATOR_FLATE_LAUNCHER_PATH) {
        val options = loadTiltaksKoordinatorFlateOptions()
        call.respondHtml {
            tiltaksKoordinatorFlateLauncherPage(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
                navIdentOptions = options.navIdenter,
                deltakerlisteOptions = options.deltakerlister,
                currentFrontendNavIdent = options.currentFrontendNavIdent,
                currentFrontendNavIdentLabel = options.currentFrontendNavIdentLabel,
            )
        }
    }

    post(TILTAKSKOORDINATOR_FLATE_KONTEKST_PATH) {
        val params = call.receiveParameters()
        val submittedNavIdent = params["navident"]?.trim().orEmpty()
        val validVeiledere = loadTiltaksKoordinatorFlateOptions().navIdenter.associateBy { it.value }

        if (submittedNavIdent.isBlank()) {
            call.redirectToTiltaksKoordinatorFlateLauncher("Velg en NAVident", isError = true)
            return@post
        }

        val veileder = validVeiledere[submittedNavIdent]
        if (veileder == null) {
            call.redirectToTiltaksKoordinatorFlateLauncher("Ukjent NAVident: $submittedNavIdent", isError = true)
            return@post
        }

        FrontendAuthState.updateNavIdent(submittedNavIdent)
        call.redirectToTiltaksKoordinatorFlateLauncher("Oppdatert kontekst til ${veileder.label}")
    }

    post(TILTAKSKOORDINATOR_FLATE_OPEN_PATH) {
        val params = call.receiveParameters()
        val deltakerlisteId = params["deltakerliste_id"]?.trim().orEmpty()
        val validIds = loadTiltaksKoordinatorFlateOptions().deltakerlister.map { it.value }.toSet()

        if (deltakerlisteId.isBlank()) {
            call.redirectToTiltaksKoordinatorFlateLauncher("Velg en deltakerliste", isError = true)
            return@post
        }

        if (!validIds.contains(deltakerlisteId)) {
            call.redirectToTiltaksKoordinatorFlateLauncher("Ukjent deltakerlisteId: $deltakerlisteId", isError = true)
            return@post
        }

        val encodedId = URLEncoder.encode(deltakerlisteId, StandardCharsets.UTF_8)
        call.respondRedirect("$TILTAKSKOORDINATOR_FLATE_URL/gjennomforinger/$encodedId/deltakerliste")
    }
}

private fun loadTiltaksKoordinatorFlateOptions(): TiltaksKoordinatorFlateOptions {
    val navIdenter = fetchNomRessurser()
        .sortedBy { it.navident }
        .map {
            TiltaksKoordinatorSelectOption(
                value = it.navident,
                label = "${it.navident} - ${it.visningsnavn}",
            )
        }

    val deltakerlister = fetchGjennomforinger()
        .sortedBy { it.id }
        .map {
            val labelSuffix = listOfNotNull(it.navn?.takeIf(String::isNotBlank), it.tiltakskode)
                .joinToString(" - ")
            TiltaksKoordinatorSelectOption(
                value = it.id,
                label = if (labelSuffix.isBlank()) it.id else "${it.id} - $labelSuffix",
            )
        }

    val currentFrontendNavIdent = FrontendAuthState.getNavIdent()
    val currentFrontendNavIdentLabel = currentFrontendNavIdent?.let { navIdent ->
        navIdenter.firstOrNull { it.value == navIdent }?.label ?: navIdent
    } ?: "Ikke satt enda"

    return TiltaksKoordinatorFlateOptions(
        navIdenter = navIdenter,
        deltakerlister = deltakerlister,
        currentFrontendNavIdent = currentFrontendNavIdent,
        currentFrontendNavIdentLabel = currentFrontendNavIdentLabel,
    )
}

private fun HTML.tiltaksKoordinatorFlateLauncherPage(
    message: String?,
    isError: Boolean,
    navIdentOptions: List<TiltaksKoordinatorSelectOption>,
    deltakerlisteOptions: List<TiltaksKoordinatorSelectOption>,
    currentFrontendNavIdent: String?,
    currentFrontendNavIdentLabel: String,
) {
    head {
        title("Start tiltakskoordinator-flate")
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        simNavHeaderStyles()
        simNavFormPageStyles(fieldSelector = "select")
        simNavLauncherUiStyles()
    }

    body {
        simNavHeader(TILTAKSKOORDINATOR_FLATE_LAUNCHER_PATH)
        main {
            h1 { +"Start tiltakskoordinator-flate" }
            p { +"Velg kontekst og åpne ønsket deltakerliste." }

            simNavLauncherMessage(message, isError)

            section(classes = "frontend-auth-panel") {
                h2 { +"Kontekst" }
                p(classes = "frontend-auth-panel__current") {
                    +"Aktiv NAVident i frontend-token: $currentFrontendNavIdentLabel"
                }
                if (currentFrontendNavIdent == null) {
                    p(classes = "frontend-auth-panel__hint") {
                        +"Frontend-token kan ikke hentes for proxy før NAVident er valgt."
                    }
                }
                form(action = TILTAKSKOORDINATOR_FLATE_KONTEKST_PATH, method = FormMethod.post) {
                    div("field") {
                        label {
                            htmlFor = "navident"
                            +"Innlogget veileder"
                        }
                        select {
                            id = "navident"
                            name = "navident"
                            required = true
                            option {
                                value = ""
                                selected = currentFrontendNavIdent == null
                                +"Velg veileder"
                            }
                            navIdentOptions.forEach { option ->
                                option {
                                    value = option.value
                                    selected = option.value == currentFrontendNavIdent
                                    +option.label
                                }
                            }
                        }
                    }
                    button(type = ButtonType.submit) { +"Oppdater kontekst" }
                }
            }

            if (currentFrontendNavIdent == null) {
                p { +"Velg kontekst ovenfor for a aktivere oppstart av flaten." }
            } else {
                form(action = TILTAKSKOORDINATOR_FLATE_OPEN_PATH, method = FormMethod.post) {
                    target = "_blank"
                    div("field") {
                        label {
                            htmlFor = "deltakerliste_id"
                            +"Deltakerliste"
                        }
                        select {
                            id = "deltakerliste_id"
                            name = "deltakerliste_id"
                            required = true
                            deltakerlisteOptions.forEachIndexed { index, option ->
                                option {
                                    value = option.value
                                    selected = index == 0
                                    +option.label
                                }
                            }
                        }
                    }

                    button(type = ButtonType.submit) { +"Aapne tiltakskoordinator-flate" }
                }
            }
        }
    }
}

private data class TiltaksKoordinatorFlateOptions(
    val navIdenter: List<TiltaksKoordinatorSelectOption>,
    val deltakerlister: List<TiltaksKoordinatorSelectOption>,
    val currentFrontendNavIdent: String?,
    val currentFrontendNavIdentLabel: String,
)

private data class TiltaksKoordinatorSelectOption(
    val value: String,
    val label: String,
)

private suspend fun io.ktor.server.application.ApplicationCall.redirectToTiltaksKoordinatorFlateLauncher(
    message: String,
    isError: Boolean = false,
) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$TILTAKSKOORDINATOR_FLATE_LAUNCHER_PATH?message=$encodedMessage&isError=$isError")
}

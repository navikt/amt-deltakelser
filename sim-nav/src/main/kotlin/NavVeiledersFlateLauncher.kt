import db.AmtDeltakerRepository
import db.DeltakerOption
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import sharedui.simNavFormPageStyles
import sharedui.simNavHeader
import sharedui.simNavHeaderStyles
import tjenester.auth.FrontendAuthState
import tjenester.nav.nom.fetchNomRessurser
import tjenester.nav.norg.NorgDataSource
import tjenester.nav.pdl.PdlDataSource
import tjenester.nav.valp.fetchGjennomforinger
import tjenester.nav.veilarbvedtaksstotte.buildVeilarbvedtaksstotteFnrOptions
import tjenester.nav.veilarbvedtaksstotte.fetchVeilarbvedtaksstottePersons
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val NAV_VEILEDERS_FLATE_LAUNCHER_PATH = "/nav-veileders-flate"
private const val NAV_VEILEDERS_FLATE_URL = "http://localhost:3004"
private const val NAV_VEILEDERS_FLATE_FRONTEND_AUTH_PATH = "$NAV_VEILEDERS_FLATE_LAUNCHER_PATH/frontend-auth"
private const val NAV_VEILEDERS_FLATE_BRUKER_PATH = "$NAV_VEILEDERS_FLATE_LAUNCHER_PATH/bruker"

fun Route.navVeiledersFlateLauncherRoutes(
    pdlDataSource: PdlDataSource,
    norgDataSource: NorgDataSource,
    amtDeltakerRepository: AmtDeltakerRepository,
) {
    get(NAV_VEILEDERS_FLATE_LAUNCHER_PATH) {
        val navVeiledersFlateOptions = loadNavVeiledersFlateOptions(pdlDataSource, norgDataSource, amtDeltakerRepository)
        call.respondHtml {
            navVeiledersFlateLauncherPage(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
                personOptions = navVeiledersFlateOptions.persons,
                veilederOptions = navVeiledersFlateOptions.veiledere,
                unitOptions = navVeiledersFlateOptions.units,
                deltakerlisteOptions = navVeiledersFlateOptions.deltakerlister,
                tiltakskodeOptions = navVeiledersFlateOptions.tiltakskoder,
                currentFrontendNavIdent = navVeiledersFlateOptions.currentFrontendNavIdent,
                currentFrontendNavIdentLabel = navVeiledersFlateOptions.currentFrontendNavIdentLabel,
                currentBrukerFnr = navVeiledersFlateOptions.currentBrukerFnr,
                currentBrukerLabel = navVeiledersFlateOptions.currentBrukerLabel,
                deltakerOptions = navVeiledersFlateOptions.deltakere,
            )
        }
    }

    post(NAV_VEILEDERS_FLATE_FRONTEND_AUTH_PATH) {
        val submittedNavIdent = call.receiveParameters()["navident"]?.trim().orEmpty()
        val validVeiledere = fetchNomRessurser().associateBy { it.navident }

        if (submittedNavIdent.isBlank()) {
            call.redirectToNavVeiledersFlateLauncher("Velg en NAVident", isError = true)
            return@post
        }

        val veileder = validVeiledere[submittedNavIdent]
        if (veileder == null) {
            call.redirectToNavVeiledersFlateLauncher("Ukjent NAVident: $submittedNavIdent", isError = true)
            return@post
        }

        FrontendAuthState.updateNavIdent(submittedNavIdent)
        call.redirectToNavVeiledersFlateLauncher("Oppdatert frontend-NAVident til ${veileder.navident} - ${veileder.visningsnavn}")
    }

    post(NAV_VEILEDERS_FLATE_BRUKER_PATH) {
        val submittedFnr = call.receiveParameters()["bruker_fnr"]?.trim().orEmpty()
        val validBrukere = loadNavVeiledersFlateOptions(pdlDataSource, norgDataSource, amtDeltakerRepository)
            .persons.associate { it.value to it.label }

        if (submittedFnr.isBlank()) {
            call.redirectToNavVeiledersFlateLauncher("Velg en bruker", isError = true)
            return@post
        }

        if (!validBrukere.containsKey(submittedFnr)) {
            call.redirectToNavVeiledersFlateLauncher("Ukjent bruker: $submittedFnr", isError = true)
            return@post
        }

        VeilederAuthState.updateBrukerFnr(submittedFnr)
        call.redirectToNavVeiledersFlateLauncher("Oppdatert bruker til ${validBrukere[submittedFnr]}")
    }
}

private fun loadNavVeiledersFlateOptions(
    pdlDataSource: PdlDataSource,
    norgDataSource: NorgDataSource,
    amtDeltakerRepository: AmtDeltakerRepository,
): NavVeiledersFlateOptions {
    val veiledere = fetchNomRessurser()
        .sortedBy { it.navident }
        .map {
            SelectOption(
                value = it.navident,
                label = "${it.navident} - ${it.visningsnavn}",
            )
        }

    val currentFrontendNavIdent = FrontendAuthState.getNavIdent()
    val currentFrontendNavIdentLabel = currentFrontendNavIdent?.let { navIdent ->
        veiledere.firstOrNull { it.value == navIdent }?.label ?: navIdent
    } ?: "Ikke satt enda"

    val pdlNamesByFnr = buildVeilarbvedtaksstotteFnrOptions(pdlDataSource).associate { option ->
        option.fnr to option.label.substringAfter(" - ").takeIf { it != option.fnr }.orEmpty()
    }

    val persons = fetchVeilarbvedtaksstottePersons()
        .sortedWith(compareBy({ pdlNamesByFnr[it.fnr].orEmpty().ifBlank { it.fnr } }, { it.fnr }))
        .map { person ->
            val personName = pdlNamesByFnr[person.fnr].orEmpty().ifBlank { person.fnr }
            val innsatsgruppe = person.innsatsgruppe?.name ?: "(ingen innsatsgruppe)"
            SelectOption(
                value = person.fnr,
                label = "$personName - $innsatsgruppe",
            )
        }

    val units = norgDataSource.allEnheter()
        .map {
            SelectOption(
                value = it.enhetNr,
                label = "${it.enhetNr} - ${it.navn}",
            )
        }

    val deltakerlister = fetchGjennomforinger()
        .sortedBy { it.id }
        .map {
            val labelSuffix = listOfNotNull(it.navn?.takeIf(String::isNotBlank), it.tiltakskode)
                .joinToString(" - ")
            SelectOption(
                value = it.id,
                label = if (labelSuffix.isBlank()) it.id else "${it.id} - $labelSuffix",
            )
        }

    val tiltakskoder = Tiltakskode.entries
        .map {
            SelectOption(
                value = it.name,
                label = it.name,
            )
        }

    val currentBrukerFnr = VeilederAuthState.getBrukerFnr()
    val currentBrukerLabel = currentBrukerFnr?.let { fnr ->
        persons.firstOrNull { it.value == fnr }?.label ?: fnr
    } ?: "Ikke satt enda"

    val deltakere = currentBrukerFnr?.let { fnr ->
        runCatching { amtDeltakerRepository.getDeltakereForPersonident(fnr) }
            .onFailure { println("Failed to fetch deltakere for fnr $fnr: ${it.message}") }
            .getOrElse { emptyList() }
    } ?: emptyList()

    return NavVeiledersFlateOptions(
        persons = persons,
        veiledere = veiledere,
        units = units,
        deltakerlister = deltakerlister,
        tiltakskoder = tiltakskoder,
        currentFrontendNavIdent = currentFrontendNavIdent,
        currentFrontendNavIdentLabel = currentFrontendNavIdentLabel,
        currentBrukerFnr = currentBrukerFnr,
        currentBrukerLabel = currentBrukerLabel,
        deltakere = deltakere,
    )
}

private fun HTML.navVeiledersFlateLauncherPage(
    message: String?,
    isError: Boolean,
    personOptions: List<SelectOption>,
    veilederOptions: List<SelectOption>,
    unitOptions: List<SelectOption>,
    deltakerlisteOptions: List<SelectOption>,
    tiltakskodeOptions: List<SelectOption>,
    currentFrontendNavIdent: String?,
    currentFrontendNavIdentLabel: String,
    currentBrukerFnr: String?,
    currentBrukerLabel: String,
    deltakerOptions: List<DeltakerOption>,
) {
    head {
        title("Start nav-veileders-flate")
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        simNavHeaderStyles()
        simNavFormPageStyles(fieldSelector = "select")
        style {
            unsafe {
                +"""
                .inline-choice { display: flex; align-items: center; gap: 0.5rem; }
                .message { padding: 0.75rem; border-radius: 6px; margin-bottom: 1rem; }
                .message--ok { background: #ebfbee; border: 1px solid #b2f2bb; }
                .message--error { background: #fff5f5; border: 1px solid #ffc9c9; }
                .frontend-auth-panel { border: 1px solid #d8d8d8; border-radius: 6px; padding: 1rem; margin-bottom: 1rem; background: #fafafa; }
                .frontend-auth-panel__current { font-weight: 600; margin-bottom: 0.75rem; }
                .frontend-auth-panel__hint { margin-bottom: 0.75rem; color: #595959; }
                .frontend-auth-panel form { border: 0; padding: 0; border-radius: 0; background: transparent; }
                p { margin-top: 0; }
                """.trimIndent()
            }
        }
    }

    body {
        simNavHeader(NAV_VEILEDERS_FLATE_LAUNCHER_PATH)
        main {
            h1 { +"Start nav-veileders-flate" }
            p { +"Velg personident, enhet og hvilken inngang du vil starte med." }

            if (message != null) {
                p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                    +message
                }
            }

            section(classes = "frontend-auth-panel") {
                h2 { +"Frontend NAVident" }
                p(classes = "frontend-auth-panel__current") {
                    +"Aktiv NAVident i frontend-token: $currentFrontendNavIdentLabel"
                }
                if (currentFrontendNavIdent == null) {
                    p(classes = "frontend-auth-panel__hint") {
                        +"Frontend-token kan ikke hentes for proxy før NAVident er valgt."
                    }
                }
                form(action = NAV_VEILEDERS_FLATE_FRONTEND_AUTH_PATH, method = FormMethod.post) {
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
                            veilederOptions.forEach { option ->
                                option {
                                    value = option.value
                                    selected = option.value == currentFrontendNavIdent
                                    +option.label
                                }
                            }
                        }
                    }
                    button(type = ButtonType.submit) { +"Oppdater NAVident" }
                }
            }

            section(classes = "frontend-auth-panel") {
                h2 { +"Valgt bruker (for deltakerId-rute)" }
                p(classes = "frontend-auth-panel__current") {
                    +"Aktiv bruker: $currentBrukerLabel"
                }
                form(action = NAV_VEILEDERS_FLATE_BRUKER_PATH, method = FormMethod.post) {
                    div("field") {
                        label {
                            htmlFor = "bruker_fnr"
                            +"Velg bruker"
                        }
                        select {
                            id = "bruker_fnr"
                            name = "bruker_fnr"
                            required = true
                            option {
                                value = ""
                                selected = currentBrukerFnr == null
                                +"Velg bruker"
                            }
                            personOptions.forEach { option ->
                                option {
                                    value = option.value
                                    selected = option.value == currentBrukerFnr
                                    +option.label
                                }
                            }
                        }
                    }
                    button(type = ButtonType.submit) { +"Oppdater bruker" }
                }
            }

            form(action = NAV_VEILEDERS_FLATE_URL, method = FormMethod.get) {
                id = "nav-veileders-flate-form"
                target = "_blank"

                div("field") {
                    label {
                        htmlFor = "person_ident"
                        +"Bruker"
                    }
                    select {
                        id = "person_ident"
                        name = "person_ident"
                        required = true
                        personOptions.forEachIndexed { index, option ->
                            option {
                                value = option.value
                                selected = index == 0
                                +option.label
                            }
                        }
                    }
                }

                div("field") {
                    label {
                        htmlFor = "enhet_id"
                        +"Enhet"
                    }
                    select {
                        id = "enhet_id"
                        name = "enhet_id"
                        required = true
                        unitOptions.forEach { option ->
                            option {
                                value = option.value
                                selected = option.value == "0315"
                                +option.label
                            }
                        }
                    }
                }

                div("field") {
                    label { +"Inngang" }

                    div("inline-choice") {
                        input(type = InputType.radio) {
                            id = "route_deltakerliste"
                            name = "route_mode"
                            value = "deltakerliste"
                            checked = true
                        }
                        label {
                            htmlFor = "route_deltakerliste"
                            +"Meld pa med deltakerlisteId"
                        }
                    }

                    select {
                        id = "deltakerliste_id"
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

                div("field") {
                    div("inline-choice") {
                        input(type = InputType.radio) {
                            id = "route_tiltakskode"
                            name = "route_mode"
                            value = "tiltakskode"
                        }
                        label {
                            htmlFor = "route_tiltakskode"
                            +"Meld pa med tiltakskode"
                        }
                    }

                    select {
                        id = "tiltakskode"
                        required = true
                        tiltakskodeOptions.forEachIndexed { index, option ->
                            option {
                                value = option.value
                                selected = index == 0
                                +option.label
                            }
                        }
                    }
                }

                div("field") {
                    div("inline-choice") {
                        input(type = InputType.radio) {
                            id = "route_deltaker"
                            name = "route_mode"
                            value = "deltaker"
                        }
                        label {
                            htmlFor = "route_deltaker"
                            +"Åpne deltakelse med deltakerId"
                        }
                    }

                    select {
                        id = "deltaker_id"
                        required = true
                        if (deltakerOptions.isEmpty()) {
                            option {
                                value = ""
                                disabled = true
                                +"Ingen deltakelser for valgt bruker"
                            }
                        } else {
                            deltakerOptions.forEachIndexed { index, option ->
                                val statusLabel = option.status?.let { " [$it]" } ?: ""
                                this@select.option {
                                    value = option.id.toString()
                                    selected = index == 0
                                    +"${option.id} - ${option.deltakerlisteNavn}$statusLabel"
                                }
                            }
                        }
                    }
                }

                button(type = ButtonType.submit) { +"Aapne valgt inngang" }
            }

            script {
                unsafe {
                    +"""
                    (() => {
                      const form = document.getElementById('nav-veileders-flate-form');
                      if (!form) return;

                      form.addEventListener('submit', () => {
                        const routeMode = document.querySelector('input[name="route_mode"]:checked')?.value;
                        const deltakerlisteId = document.getElementById('deltakerliste_id')?.value;
                        const tiltakskode = document.getElementById('tiltakskode')?.value;
                        const deltakerId = document.getElementById('deltaker_id')?.value;
                        const basePath = '/arbeidsmarkedstiltak/deltakelse';

                        let routePath;
                        if (routeMode === 'tiltakskode') {
                          routePath = basePath + '/tiltak/' + encodeURIComponent(tiltakskode) + '/';
                        } else if (routeMode === 'deltaker') {
                          routePath = basePath + '/deltaker/' + encodeURIComponent(deltakerId);
                        } else {
                          routePath = basePath + '/' + encodeURIComponent(deltakerlisteId);
                        }

                        form.action = '${NAV_VEILEDERS_FLATE_URL}' + routePath;
                      });
                    })();
                    """.trimIndent()
                }
            }
        }
    }
}

private data class NavVeiledersFlateOptions(
    val persons: List<SelectOption>,
    val veiledere: List<SelectOption>,
    val units: List<SelectOption>,
    val deltakerlister: List<SelectOption>,
    val tiltakskoder: List<SelectOption>,
    val currentFrontendNavIdent: String?,
    val currentFrontendNavIdentLabel: String,
    val currentBrukerFnr: String?,
    val currentBrukerLabel: String,
    val deltakere: List<DeltakerOption>,
)

private data class SelectOption(
    val value: String,
    val label: String,
)

object VeilederAuthState {
    private var brukerFnr: String? = null

    fun updateBrukerFnr(fnr: String) {
        brukerFnr = fnr
    }

    fun getBrukerFnr(): String? = brukerFnr
}

private suspend fun io.ktor.server.application.ApplicationCall.redirectToNavVeiledersFlateLauncher(
    message: String,
    isError: Boolean = false,
) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$NAV_VEILEDERS_FLATE_LAUNCHER_PATH?message=$encodedMessage&isError=$isError")
}



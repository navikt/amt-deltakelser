import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import pdl.PdlDataSource
import valp.fetchGjennomforinger

private const val NAV_VEILEDERS_FLATE_LAUNCHER_PATH = "/nav-veileders-flate"
private const val NAV_VEILEDERS_FLATE_URL = "http://localhost:3004"

fun Route.navVeiledersFlateLauncherRoutes(
    pdlDataSource: PdlDataSource,
    norgDataSource: NorgDataSource,
) {
    get(NAV_VEILEDERS_FLATE_LAUNCHER_PATH) {
        val navVeiledersFlateOptions = loadNavVeiledersFlateOptions(pdlDataSource, norgDataSource)
        call.respondHtml {
            navVeiledersFlateLauncherPage(
                personOptions = navVeiledersFlateOptions.persons,
                unitOptions = navVeiledersFlateOptions.units,
                deltakerlisteOptions = navVeiledersFlateOptions.deltakerlister,
                tiltakskodeOptions = navVeiledersFlateOptions.tiltakskoder,
            )
        }
    }
}

private fun loadNavVeiledersFlateOptions(
    pdlDataSource: PdlDataSource,
    norgDataSource: NorgDataSource,
): NavVeiledersFlateOptions {
    val persons = pdlDataSource.allPersons()
        .toList()
        .sortedBy { (personident, _) -> personident }
        .map { (personident, person) ->
            val displayName = person.navn.firstOrNull()?.let {
                listOfNotNull(it.fornavn, it.mellomnavn, it.etternavn)
                    .joinToString(" ")
            }
            SelectOption(
                value = personident,
                label = if (displayName.isNullOrBlank()) personident else "$personident - $displayName",
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

    return NavVeiledersFlateOptions(
        persons = persons,
        units = units,
        deltakerlister = deltakerlister,
        tiltakskoder = tiltakskoder,
    )
}

private fun HTML.navVeiledersFlateLauncherPage(
    personOptions: List<SelectOption>,
    unitOptions: List<SelectOption>,
    deltakerlisteOptions: List<SelectOption>,
    tiltakskodeOptions: List<SelectOption>,
) {
    head {
        title("Start nav-veileders-flate")
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        style {
            unsafe {
                +"""
                body { font-family: sans-serif; margin: 2rem; max-width: 44rem; }
                form { border: 1px solid #d8d8d8; border-radius: 6px; padding: 1rem; }
                .field { margin-bottom: 0.75rem; display: flex; flex-direction: column; gap: 0.25rem; }
                select, button { max-width: 34rem; padding: 0.45rem; }
                .inline-choice { display: flex; align-items: center; gap: 0.5rem; }
                p { margin-top: 0; }
                """.trimIndent()
            }
        }
    }

    body {
        main {
            h1 { +"Start nav-veileders-flate" }
            p { +"Velg personident, enhet og hvilken inngang du vil starte med." }

            form(action = NAV_VEILEDERS_FLATE_URL, method = FormMethod.get) {
                id = "nav-veileders-flate-form"
                target = "_blank"

                div("field") {
                    label {
                        htmlFor = "veileder_person_ident"
                        +"Veileder"
                    }
                    select {
                        id = "veileder_person_ident"
                        name = "veileder_person_ident"
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
                        const basePath = '/arbeidsmarkedstiltak/deltakelse';

                        const routePath = routeMode === 'tiltakskode'
                          ? basePath + '/tiltak/' + encodeURIComponent(tiltakskode) + '/'
                          : basePath + '/' + encodeURIComponent(deltakerlisteId);

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
    val units: List<SelectOption>,
    val deltakerlister: List<SelectOption>,
    val tiltakskoder: List<SelectOption>,
)

private data class SelectOption(
    val value: String,
    val label: String,
)



import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*
import pdl.PdlDataSource

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

    return NavVeiledersFlateOptions(persons = persons, units = units)
}

private fun HTML.navVeiledersFlateLauncherPage(
    personOptions: List<SelectOption>,
    unitOptions: List<SelectOption>,
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
                p { margin-top: 0; }
                """.trimIndent()
            }
        }
    }

    body {
        main {
            h1 { +"Start nav-veileders-flate" }
            p { +"Velg personident og enhet fra simulerte data, og applikasjonen aapnes med riktige URL-parametere." }

            form(action = NAV_VEILEDERS_FLATE_URL, method = FormMethod.get) {
                target = "_blank"

                div("field") {
                    label {
                        htmlFor = "initial_person_ident"
                        +"Veileder"
                    }
                    select {
                        id = "initial_person_ident"
                        name = "initial_person_ident"
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
                        htmlFor = "initial_enhet_id"
                        +"Enhet"
                    }
                    select {
                        id = "initial_enhet_id"
                        name = "initial_enhet_id"
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

                button(type = ButtonType.submit) { +"Aapne localhost:3004" }
            }
        }
    }
}

private data class NavVeiledersFlateOptions(
    val persons: List<SelectOption>,
    val units: List<SelectOption>,
)

private data class SelectOption(
    val value: String,
    val label: String,
)



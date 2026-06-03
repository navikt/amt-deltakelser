package tjenester.nav.veilarbvedtaksstotte

import kotlinx.html.*
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import sharedui.simNavCrudPageStyles
import sharedui.simNavFormPageStyles
import sharedui.simNavHeader
import sharedui.simNavHeaderStyles
import tjenester.nav.pdl.PdlDataSource

data class VeilarbvedtaksstotteFnrOption(
    val fnr: String,
    val label: String,
)

fun buildVeilarbvedtaksstotteFnrOptions(pdlDataSource: PdlDataSource): List<VeilarbvedtaksstotteFnrOption> {
    val pdlPersons = pdlDataSource.allPersons()
    return pdlPersons.keys
        .filter { it.matches(Regex("\\d{11}")) }
        .distinct()
        .sorted()
        .map { fnr ->
            val navn = pdlPersons[fnr]?.navn?.firstOrNull()?.let {
                listOfNotNull(it.fornavn, it.mellomnavn, it.etternavn).joinToString(" ")
            }.orEmpty()
            VeilarbvedtaksstotteFnrOption(
                fnr = fnr,
                label = if (navn.isBlank()) fnr else "$fnr - $navn",
            )
        }
}

fun HTML.veilarbvedtaksstottePage(
    persons: List<VeilarbvedtaksstottePersonRow>,
    message: String?,
    isError: Boolean,
    newPersonPath: String,
    editPersonPathPrefix: String,
    pdlNamesByFnr: Map<String, String>,
) {
    head {
        title("Veilarbvedtaksstotte - Simulator")
        simNavHeaderStyles()
        simNavCrudPageStyles()
    }

    body {
        simNavHeader(VEILARBVEDTAKSSTOTTE_PATH_PREFIX)
        h1 { +"Veilarbvedtaksstotte" }
        p { +"Tjeneste for fatting av 14a-vedtak (vedtak om oppfølging)" }

        if (message != null) {
            p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                +message
            }
        }

        h2 {
            +"Vedtak (${persons.size})"
            a(classes = "add-button", href = newPersonPath) {
                title = "Legg til vedtak"
                +"+"
            }
        }

        if (persons.isEmpty()) {
            div(classes = "empty") { +"Ingen vedtak i databasen" }
        } else {
            table {
                thead {
                    tr {
                        th { +"Fnr" }
                        th { +"Navn" }
                        th { +"Innsatsgruppe" }
                        th { +"Handlinger" }
                    }
                }
                tbody {
                    persons.forEach { row ->
                        tr {
                            td { span(classes = "id") { +row.fnr } }
                            td { +(pdlNamesByFnr[row.fnr].orEmpty().ifBlank { "-" }) }
                            td { +(row.innsatsgruppe?.name ?: "(ingen)") }
                            td(classes = "actions") {
                                a(href = "$editPersonPathPrefix/${row.fnr}/edit") { +"Rediger" }
                                form(
                                    action = "$editPersonPathPrefix/${row.fnr}/delete",
                                    method = FormMethod.post,
                                    classes = "inline-form",
                                ) {
                                    button(type = ButtonType.submit, classes = "danger-link") { +"Slett" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun HTML.veilarbvedtaksstottePersonFormPage(
    defaults: VeilarbvedtaksstottePersonFormDefaults,
    actionPath: String,
    backPath: String,
    fnrOptions: List<VeilarbvedtaksstotteFnrOption>,
) {
    head {
        title("New person - Veilarbvedtaksstotte")
        simNavHeaderStyles()
        veilarbvedtaksstotteFormStyles()
    }
    body {
        simNavHeader(VEILARBVEDTAKSSTOTTE_PATH_PREFIX)
        h1 { +"Ny person" }
        p { a(href = backPath) { +"<- Tilbake" } }

        form(action = actionPath, method = FormMethod.post) {
            veilarbvedtaksstotteFnrField(fnrOptions, defaults.fnr)
            veilarbvedtaksstotteInnsatsgruppeField(defaults.innsatsgruppe)
            button(type = ButtonType.submit) { +"Lagre" }
        }
    }
}

fun HTML.veilarbvedtaksstottePersonEditFormPage(
    defaults: VeilarbvedtaksstottePersonFormDefaults,
    actionPath: String,
    backPath: String,
    personName: String,
) {
    head {
        title("Edit person - Veilarbvedtaksstotte")
        simNavHeaderStyles()
        veilarbvedtaksstotteFormStyles()
    }
    body {
        simNavHeader(VEILARBVEDTAKSSTOTTE_PATH_PREFIX)
        h1 { +"Rediger person" }
        p { a(href = backPath) { +"<- Tilbake" } }
        p { +"Fnr: ${defaults.fnr}${if (personName.isBlank()) "" else " - $personName"}" }

        form(action = actionPath, method = FormMethod.post) {
            veilarbvedtaksstotteInnsatsgruppeField(defaults.innsatsgruppe)
            button(type = ButtonType.submit) { +"Lagre endringer" }
        }
    }
}

private fun HEAD.veilarbvedtaksstotteFormStyles() {
    simNavFormPageStyles(fieldSelector = "input, select")
}

private fun FORM.veilarbvedtaksstotteFnrField(
    options: List<VeilarbvedtaksstotteFnrOption>,
    selectedValue: String,
) {
    div("field") {
        label { +"Fnr" }
        select {
            name = "fnr"
            required = true
            options.forEach { option ->
                option {
                    value = option.fnr
                    selected = selectedValue == option.fnr
                    +option.label
                }
            }
        }
    }
}

private fun FORM.veilarbvedtaksstotteInnsatsgruppeField(selectedValue: InnsatsgruppeV2?) {
    div("field") {
        label { +"Innsatsgruppe (valgfri)" }
        select {
            name = "innsatsgruppe"
            option {
                value = ""
                selected = selectedValue == null
                +"(ingen)"
            }
            InnsatsgruppeV2.entries.forEach { entry ->
                option {
                    value = entry.name
                    selected = selectedValue == entry
                    +entry.name
                }
            }
        }
    }
}


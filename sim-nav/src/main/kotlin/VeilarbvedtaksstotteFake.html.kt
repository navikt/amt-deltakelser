import kotlinx.html.*
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import pdl.PdlDataSource
import sharedui.simNavHeader
import sharedui.simNavHeaderStyles

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
        style {
            unsafe {
                raw(
                    """
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { color: #333; }
                    h2 { color: #666; margin-top: 30px; border-bottom: 2px solid #ddd; padding-bottom: 10px; display: flex; align-items: center; justify-content: space-between; }
                    .empty { color: #999; font-style: italic; padding: 20px; }
                    .message { padding: 0.75rem; border-radius: 6px; margin-bottom: 1rem; }
                    .message--ok { background: #ebfbee; border: 1px solid #b2f2bb; }
                    .message--error { background: #fff5f5; border: 1px solid #ffc9c9; }
                    .add-button {
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        width: 28px;
                        height: 28px;
                        border-radius: 14px;
                        border: 1px solid #0d6efd;
                        color: #0d6efd;
                        text-decoration: none;
                        font-size: 20px;
                        line-height: 1;
                    }
                    table { border-collapse: collapse; width: 100%; margin-top: 10px; }
                    th { background-color: #f0f0f0; border: 1px solid #ddd; padding: 10px; text-align: left; font-weight: bold; }
                    td { border: 1px solid #ddd; padding: 10px; }
                    tr:nth-child(even) { background-color: #f9f9f9; }
                    .id { font-family: monospace; font-size: 0.9em; color: #666; }
                    .actions { display: flex; gap: 0.5rem; }
                    .inline-form { margin: 0; }
                    .danger-link { color: #b42318; background: none; border: none; padding: 0; cursor: pointer; font: inherit; text-decoration: underline; }
                    """.trimIndent(),
                )
            }
        }
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
    style {
        unsafe {
            +"""
            body { font-family: sans-serif; margin: 2rem; max-width: 60rem; }
            form { border: 1px solid #d8d8d8; padding: 1rem; border-radius: 6px; }
            .field { margin-bottom: 0.75rem; display: flex; flex-direction: column; gap: 0.25rem; }
            input, select { max-width: 52rem; padding: 0.4rem; }
            button { padding: 0.5rem 0.8rem; }
            """.trimIndent()
        }
    }
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


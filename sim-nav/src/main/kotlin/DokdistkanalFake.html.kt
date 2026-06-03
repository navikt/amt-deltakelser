import kotlinx.html.*
import pdl.PdlDataSource
import sharedui.simNavHeader
import sharedui.simNavHeaderStyles

data class DokdistkanalPersonidentOption(
    val personident: String,
    val label: String,
)

fun buildDokdistkanalPersonidentOptions(pdlDataSource: PdlDataSource): List<DokdistkanalPersonidentOption> {
    val pdlPersons = pdlDataSource.allPersons()
    return pdlPersons.keys
        .filter { it.matches(Regex("\\d{11}")) }
        .distinct()
        .sorted()
        .map { personident ->
            val navn = pdlPersons[personident]?.navn?.firstOrNull()?.let {
                listOfNotNull(it.fornavn, it.mellomnavn, it.etternavn).joinToString(" ")
            }.orEmpty()

            DokdistkanalPersonidentOption(
                personident = personident,
                label = if (navn.isBlank()) personident else "$personident - $navn",
            )
        }
}

fun HTML.dokdistkanalPage(
    persons: List<DokdistkanalPersonRow>,
    message: String?,
    isError: Boolean,
    newPersonPath: String,
    editPersonPathPrefix: String,
    pdlNamesByPersonident: Map<String, String>,
) {
    head {
        title("Dokdistkanal - Simulator")
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
        simNavHeader(DOKDISTKANAL_PATH_PREFIX)
        h1 { +"Dokdistkanal - Simulator" }

        if (message != null) {
            p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                +message
            }
        }

        h2 {
            +"Persons (${persons.size})"
            a(classes = "add-button", href = newPersonPath) {
                title = "Add new person"
                +"+"
            }
        }

        if (persons.isEmpty()) {
            div(classes = "empty") { +"No persons in database" }
        } else {
            table {
                thead {
                    tr {
                        th { +"Personident" }
                        th { +"Navn" }
                        th { +"Distribusjonskanal" }
                        th { +"Actions" }
                    }
                }
                tbody {
                    persons.forEach { row ->
                        tr {
                            td { span(classes = "id") { +row.personident } }
                            td { +(pdlNamesByPersonident[row.personident].orEmpty().ifBlank { "-" }) }
                            td { +row.distribusjonskanal.name }
                            td(classes = "actions") {
                                a(href = "$editPersonPathPrefix/${row.personident}/edit") { +"Edit" }
                                form(
                                    action = "$editPersonPathPrefix/${row.personident}/delete",
                                    method = FormMethod.post,
                                    classes = "inline-form",
                                ) {
                                    button(type = ButtonType.submit, classes = "danger-link") { +"Delete" }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun HTML.dokdistkanalPersonFormPage(
    defaults: DokdistkanalPersonFormDefaults,
    actionPath: String,
    backPath: String,
    personidentOptions: List<DokdistkanalPersonidentOption>,
) {
    head {
        title("New person - Dokdistkanal")
        simNavHeaderStyles()
        dokdistkanalFormStyles()
    }
    body {
        simNavHeader(DOKDISTKANAL_PATH_PREFIX)
        h1 { +"Ny person" }
        p { a(href = backPath) { +"<- Tilbake" } }

        form(action = actionPath, method = FormMethod.post) {
            dokdistkanalPersonidentField(personidentOptions, defaults.personident)
            dokdistkanalDistribusjonskanalField(defaults.distribusjonskanal)
            button(type = ButtonType.submit) { +"Lagre" }
        }
    }
}

fun HTML.dokdistkanalPersonEditFormPage(
    defaults: DokdistkanalPersonFormDefaults,
    actionPath: String,
    backPath: String,
    personName: String,
) {
    head {
        title("Edit person - Dokdistkanal")
        simNavHeaderStyles()
        dokdistkanalFormStyles()
    }
    body {
        simNavHeader(DOKDISTKANAL_PATH_PREFIX)
        h1 { +"Rediger person" }
        p { a(href = backPath) { +"<- Tilbake" } }
        p { +"Personident: ${defaults.personident}${if (personName.isBlank()) "" else " - $personName"}" }

        form(action = actionPath, method = FormMethod.post) {
            dokdistkanalDistribusjonskanalField(defaults.distribusjonskanal)
            button(type = ButtonType.submit) { +"Lagre endringer" }
        }
    }
}

private fun HEAD.dokdistkanalFormStyles() {
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

private fun FORM.dokdistkanalPersonidentField(
    options: List<DokdistkanalPersonidentOption>,
    selectedValue: String,
) {
    div("field") {
        label { +"Personident" }
        select {
            name = "personident"
            required = true
            options.forEach { option ->
                option {
                    value = option.personident
                    selected = selectedValue == option.personident
                    +option.label
                }
            }
        }
    }
}

private fun FORM.dokdistkanalDistribusjonskanalField(selectedValue: DokdistkanalDistribusjonskanal) {
    div("field") {
        label { +"Distribusjonskanal" }
        select {
            name = "distribusjonskanal"
            required = true
            DokdistkanalDistribusjonskanal.entries.forEach { entry ->
                option {
                    value = entry.name
                    selected = selectedValue == entry
                    +entry.name
                }
            }
        }
    }
}



import kotlinx.html.*
import pdl.PdlDataSource
import sharedui.simNavHeader
import sharedui.simNavCrudPageStyles
import sharedui.simNavFormPageStyles
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
        simNavCrudPageStyles()
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
    simNavFormPageStyles(fieldSelector = "input, select")
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



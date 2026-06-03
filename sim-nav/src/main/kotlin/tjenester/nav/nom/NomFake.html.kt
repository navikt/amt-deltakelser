package tjenester.nav.nom

import kotlinx.html.*
import sharedui.simNavCrudPageStyles
import sharedui.simNavFormPageStyles
import sharedui.simNavHeader
import sharedui.simNavHeaderStyles

fun HTML.nomPage(
    ressurser: List<NomRessursRow>,
    message: String?,
    isError: Boolean,
    newRessursPath: String,
    editRessursPathPrefix: String,
    pdlNamesByPersonident: Map<String, String>,
) {
    head {
        title("Nom - Simulator")
        simNavHeaderStyles()
        simNavCrudPageStyles()
    }

    body {
        simNavHeader(NOM_PATH_PREFIX)
        h1 { +"Nom" }
        p { +"NAV Organisasjonsmaster skal være masterkilde for ressurser, organisasjonsenheter inkl. organisasjonshierarkiet, samt orgtilknytning mellom ressurser og organisasjonsenheter." }

        if (message != null) {
            p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                +message
            }
        }

        h2 {
            +"Ressurser (${ressurser.size})"
            a(classes = "add-button", href = newRessursPath) {
                title = "Add new ressurs"
                +"+"
            }
        }

        if (ressurser.isEmpty()) {
            div(classes = "empty") { +"No ressurser in database" }
        } else {
            table {
                thead {
                    tr {
                        th { +"Navident" }
                        th { +"Personident" }
                        th { +"Visningsnavn" }
                        th { +"Epost" }
                        th { +"Telefon" }
                        th { +"Org-tilknytning" }
                        th { +"Actions" }
                    }
                }
                tbody {
                    ressurser.forEach { row ->
                        tr {
                            td { span(classes = "id") { +row.navident } }
                            td { span(classes = "id") { +row.personident } }
                            td { +row.visningsnavn }
                            td { +row.epost }
                            td { +row.telefon.size.toString() }
                            td { +row.orgTilknytning.size.toString() }
                            td(classes = "actions") {
                                a(href = "$editRessursPathPrefix/${row.navident}/edit") { +"Edit" }
                                form(action = "$editRessursPathPrefix/${row.navident}/delete", method = FormMethod.post, classes = "inline-form") {
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

fun HTML.nomRessursFormPage(
    defaults: NomRessursFormDefaults,
    actionPath: String,
    backPath: String,
    personidentOptions: List<NomPersonidentOption>,
) {
    head {
        title("New ressurs - Nom")
        simNavHeaderStyles()
        nomFormStyles()
    }

    body {
        simNavHeader(NOM_PATH_PREFIX)
        h1 { +"Ny ressurs" }
        p { a(href = backPath) { +"<- Tilbake" } }

        form(action = actionPath, method = FormMethod.post) {
            nomTextField("Navident", "navident", defaults.navident)
            nomPersonidentField(personidentOptions, defaults.personident)
            button(type = ButtonType.submit) { +"Lagre" }
        }
    }
}

fun HTML.nomRessursEditFormPage(
    defaults: NomRessursFormDefaults,
    actionPath: String,
    backPath: String,
    personidentOptions: List<NomPersonidentOption>,
) {
    head {
        title("Edit ressurs - Nom")
        simNavHeaderStyles()
        nomFormStyles()
    }

    body {
        simNavHeader(NOM_PATH_PREFIX)
        h1 { +"Rediger ressurs" }
        p { a(href = backPath) { +"<- Tilbake" } }
        p { +"Navident: ${defaults.navident}" }

        form(action = actionPath, method = FormMethod.post) {
            nomPersonidentField(personidentOptions, defaults.personident)
            nomTextField("Visningsnavn", "visningsnavn", defaults.visningsnavn)
            nomTextField("Fornavn", "fornavn", defaults.fornavn)
            nomTextField("Etternavn", "etternavn", defaults.etternavn)
            nomTextField("Epost", "epost", defaults.epost)
            nomTextField("Primary telefon (valgfri)", "primaryTelefon", defaults.primaryTelefon, required = false)
            nomJsonTextArea("Telefon (JSON array)", "telefon", defaults.telefonJson)
            nomJsonTextArea("Org-tilknytning (JSON array)", "orgTilknytning", defaults.orgTilknytningJson)
            button(type = ButtonType.submit) { +"Lagre endringer" }
        }
    }
}

private fun HEAD.nomFormStyles() {
    simNavFormPageStyles(fieldSelector = "input, textarea", monospaceFields = true)
}

private fun FORM.nomTextField(
    labelText: String,
    name: String,
    value: String,
    required: Boolean = true,
) {
    div("field") {
        label { +labelText }
        input(type = InputType.text, name = name) {
            this.value = value
            this.required = required
        }
    }
}

private fun FORM.nomJsonTextArea(labelText: String, name: String, value: String) {
    div("field") {
        label { +labelText }
        textArea(rows = "10") {
            this.name = name
            required = true
            +value
        }
    }
}

private fun FORM.nomPersonidentField(options: List<NomPersonidentOption>, selectedValue: String) {
    div("field") {
        label { +"Personident" }
        select {
            name = "personident"
            required = true
            options.forEachIndexed { index, option ->
                option {
                    value = option.personident
                    selected = selectedValue == option.personident || (selectedValue.isBlank() && index == 0)
                    +option.label
                }
            }
        }
    }
}


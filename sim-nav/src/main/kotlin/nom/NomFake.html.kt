package nom

import kotlinx.html.*

fun HTML.nomPage(
    ressurser: List<NomRessursRow>,
    message: String?,
    isError: Boolean,
    newRessursPath: String,
    editRessursPathPrefix: String,
) {
    head {
        title("Nom - Simulator")
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
        h1 { +"Nom - Simulator" }

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
) {
    head {
        title("New ressurs - Nom")
        nomFormStyles()
    }

    body {
        h1 { +"Ny ressurs" }
        p { a(href = backPath) { +"<- Tilbake" } }

        form(action = actionPath, method = FormMethod.post) {
            nomTextField("Navident", "navident", defaults.navident)
            nomTextField("Visningsnavn", "visningsnavn", defaults.visningsnavn)
            nomTextField("Fornavn", "fornavn", defaults.fornavn)
            nomTextField("Etternavn", "etternavn", defaults.etternavn)
            nomTextField("Epost", "epost", defaults.epost)
            nomTextField("Primary telefon (valgfri)", "primaryTelefon", defaults.primaryTelefon, required = false)
            nomJsonTextArea("Telefon (JSON array)", "telefon", defaults.telefonJson)
            nomJsonTextArea("Org-tilknytning (JSON array)", "orgTilknytning", defaults.orgTilknytningJson)
            button(type = ButtonType.submit) { +"Lagre" }
        }
    }
}

fun HTML.nomRessursEditFormPage(
    defaults: NomRessursFormDefaults,
    actionPath: String,
    backPath: String,
) {
    head {
        title("Edit ressurs - Nom")
        nomFormStyles()
    }

    body {
        h1 { +"Rediger ressurs" }
        p { a(href = backPath) { +"<- Tilbake" } }
        p { +"Navident: ${defaults.navident}" }

        form(action = actionPath, method = FormMethod.post) {
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
    style {
        unsafe {
            +"""
            body { font-family: sans-serif; margin: 2rem; max-width: 60rem; }
            form { border: 1px solid #d8d8d8; padding: 1rem; border-radius: 6px; }
            .field { margin-bottom: 0.75rem; display: flex; flex-direction: column; gap: 0.25rem; }
            input, textarea { max-width: 52rem; padding: 0.4rem; font-family: monospace; }
            button { padding: 0.5rem 0.8rem; }
            """.trimIndent()
        }
    }
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
        textArea(rows = "10", cols = "80") {
            this.name = name
            required = true
            +value
        }
    }
}


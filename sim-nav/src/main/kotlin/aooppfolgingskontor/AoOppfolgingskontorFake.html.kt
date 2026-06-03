package aooppfolgingskontor

import kotlinx.html.*

fun HTML.aoOppfolgingskontorPage(
    rows: List<AoOppfolgingskontorKontorTilhorighetRow>,
    message: String?,
    isError: Boolean,
    newPath: String,
    editPathPrefix: String,
    pdlNamesByPersonident: Map<String, String>,
) {
    head {
        title("Ao oppfolgingskontor - Simulator")
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
        h1 { +"Ao oppfolgingskontor - Simulator" }

        if (message != null) {
            p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                +message
            }
        }

        h2 {
            +"Kontor-tilhorigheter (${rows.size})"
            a(classes = "add-button", href = newPath) {
                title = "Add new kontor-tilhorighet"
                +"+"
            }
        }

        if (rows.isEmpty()) {
            div(classes = "empty") { +"No kontor-tilhorigheter in database" }
        } else {
            table {
                thead {
                    tr {
                        th { +"Ident" }
                        th { +"Navn" }
                        th { +"Arbeidsoppfolging kontorId" }
                        th { +"Arbeidsoppfolging kontorNavn" }
                        th { +"Actions" }
                    }
                }
                tbody {
                    rows.forEach { row ->
                        tr {
                            td { span(classes = "id") { +row.ident } }
                            td { +(pdlNamesByPersonident[row.ident].orEmpty().ifBlank { "-" }) }
                            td { +(row.arbeidsoppfolging?.kontorId ?: "-") }
                            td { +(row.arbeidsoppfolging?.kontorNavn ?: "-") }
                            td(classes = "actions") {
                                a(href = "$editPathPrefix/${row.ident}/edit") { +"Edit" }
                                form(action = "$editPathPrefix/${row.ident}/delete", method = FormMethod.post, classes = "inline-form") {
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

fun HTML.aoOppfolgingskontorFormPage(
    defaults: AoOppfolgingskontorFormDefaults,
    actionPath: String,
    backPath: String,
    personidentOptions: List<AoOppfolgingskontorPersonidentOption>,
    norgKontorOptions: List<AoOppfolgingskontorNorgKontorOption>,
) {
    head {
        title("New kontor-tilhorighet - Ao oppfolgingskontor")
        aoOppfolgingskontorFormStyles()
    }

    body {
        h1 { +"Ny kontor-tilhorighet" }
        p { a(href = backPath) { +"<- Tilbake" } }

        form(action = actionPath, method = FormMethod.post) {
            aoOppfolgingskontorPersonidentField(personidentOptions, defaults.ident)
            aoOppfolgingskontorNorgKontorField(norgKontorOptions, defaults.arbeidsoppfolgingKontorId)
            button(type = ButtonType.submit) { +"Lagre" }
        }
    }
}

fun HTML.aoOppfolgingskontorEditFormPage(
    defaults: AoOppfolgingskontorFormDefaults,
    actionPath: String,
    backPath: String,
    norgKontorOptions: List<AoOppfolgingskontorNorgKontorOption>,
    personName: String,
) {
    head {
        title("Edit kontor-tilhorighet - Ao oppfolgingskontor")
        aoOppfolgingskontorFormStyles()
    }

    body {
        h1 { +"Rediger kontor-tilhorighet" }
        p { a(href = backPath) { +"<- Tilbake" } }
        p { +"Ident: ${defaults.ident}${if (personName.isBlank()) "" else " - $personName"}" }

        form(action = actionPath, method = FormMethod.post) {
            aoOppfolgingskontorNorgKontorField(norgKontorOptions, defaults.arbeidsoppfolgingKontorId)
            button(type = ButtonType.submit) { +"Lagre endringer" }
        }
    }
}

private fun HEAD.aoOppfolgingskontorFormStyles() {
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

private fun FORM.aoOppfolgingskontorPersonidentField(
    options: List<AoOppfolgingskontorPersonidentOption>,
    selectedValue: String,
) {
    div("field") {
        label { +"Ident" }
        select {
            name = "ident"
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

private fun FORM.aoOppfolgingskontorNorgKontorField(
    options: List<AoOppfolgingskontorNorgKontorOption>,
    selectedValue: String,
) {
    div("field") {
        label { +"Arbeidsoppfolging kontor (valgfri)" }
        select {
            name = "arbeidsoppfolgingKontorId"
            option {
                value = ""
                selected = selectedValue.isBlank()
                +"(ingen)"
            }
            options.forEach { option ->
                option {
                    value = option.kontorId
                    selected = selectedValue == option.kontorId
                    +option.label
                }
            }
        }
    }
}


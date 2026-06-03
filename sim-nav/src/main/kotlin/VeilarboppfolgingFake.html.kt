import kotlinx.html.*

fun HTML.veilarboppfolgingPage(
    persons: List<VeilarboppfolgingPersonRow>,
    message: String?,
    isError: Boolean,
    newPersonPath: String,
    editPersonPathPrefix: String,
    pdlNamesByFnr: Map<String, String>,
) {
    head {
        title("Veilarboppfolging - Simulator")
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
                    table {
                        border-collapse: collapse;
                        width: 100%;
                        margin-top: 10px;
                    }
                    th {
                        background-color: #f0f0f0;
                        border: 1px solid #ddd;
                        padding: 10px;
                        text-align: left;
                        font-weight: bold;
                    }
                    td {
                        border: 1px solid #ddd;
                        padding: 10px;
                    }
                    tr:nth-child(even) { background-color: #f9f9f9; }
                    .id { font-family: monospace; font-size: 0.9em; color: #666; }
                    .actions { display: flex; gap: 0.5rem; }
                    .inline-form { margin: 0; }
                    .danger-link {
                        color: #b42318;
                        background: none;
                        border: none;
                        padding: 0;
                        cursor: pointer;
                        font: inherit;
                        text-decoration: underline;
                    }
                    """.trimIndent(),
                )
            }
        }
    }

    body {
        h1 { +"Veilarboppfolging - Simulator" }

        if (message != null) {
            p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                +message
            }
        }

        h2 {
            +"Personer (${persons.size})"
            a(classes = "add-button", href = newPersonPath) {
                title = "Add new person"
                +"+"
            }
        }

        if (persons.isEmpty()) {
            div(classes = "empty") { +"No personer in database" }
        } else {
            table {
                thead {
                    tr {
                        th { +"Fnr" }
                        th { +"PDL-navn" }
                        th { +"Veileder" }
                        th { +"Manuell oppfolging" }
                        th { +"Oppfolgingsperioder" }
                        th { +"Actions" }
                    }
                }
                tbody {
                    persons.forEach { row ->
                        tr {
                            td {
                                span(classes = "id") { +row.fnr }
                            }
                            td { +(pdlNamesByFnr[row.fnr]?.takeIf { it.isNotBlank() } ?: "-") }
                            td { +row.veilederIdent }
                            td { +if (row.erUnderManuellOppfolging) "true" else "false" }
                            td { +row.oppfolgingsperioder.size.toString() }
                            td(classes = "actions") {
                                a(href = "$editPersonPathPrefix/${row.fnr}/edit") { +"Edit" }
                                form(action = "$editPersonPathPrefix/${row.fnr}/delete", method = FormMethod.post, classes = "inline-form") {
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

fun HTML.veilarboppfolgingPersonFormPage(
    defaults: VeilarboppfolgingPersonFormDefaults,
    actionPath: String,
    backPath: String,
    fnrOptions: List<FnrOption>,
    veilederOptions: List<nom.NomVeilederOption>,
) {
    head {
        title("New person - Veilarboppfolging")
        veilarboppfolgingFormStyles()
    }

    body {
        h1 { +"Ny person" }
        p { a(href = backPath) { +"<- Tilbake" } }

        form(action = actionPath, method = FormMethod.post) {
            div("field") {
                label { +"Fnr" }
                select {
                    name = "fnr"
                    required = true
                    fnrOptions.forEachIndexed { index, option ->
                        option {
                            value = option.fnr
                            selected = defaults.fnr == option.fnr || (defaults.fnr.isBlank() && index == 0)
                            +option.label
                        }
                    }
                }
            }
            veilederField(veilederOptions, defaults.veilederIdent)
            veilarboppfolgingFormFields(defaults)
            button(type = ButtonType.submit) { +"Lagre" }
        }
    }
}

fun HTML.veilarboppfolgingPersonEditFormPage(
    defaults: VeilarboppfolgingPersonFormDefaults,
    actionPath: String,
    backPath: String,
    veilederOptions: List<nom.NomVeilederOption>,
) {
    head {
        title("Edit person - Veilarboppfolging")
        veilarboppfolgingFormStyles()
    }

    body {
        h1 { +"Rediger person" }
        p { a(href = backPath) { +"<- Tilbake" } }
        p { +"Fnr: ${defaults.fnr}" }

        form(action = actionPath, method = FormMethod.post) {
            veilederField(veilederOptions, defaults.veilederIdent)
            veilarboppfolgingFormFields(defaults)
            button(type = ButtonType.submit) { +"Lagre endringer" }
        }
    }
}

private fun FORM.veilarboppfolgingFormFields(defaults: VeilarboppfolgingPersonFormDefaults) {
    div("field") {
        label { +"Er under manuell oppfolging" }
        select {
            name = "erUnderManuellOppfolging"
            required = true

            option {
                value = "true"
                selected = defaults.erUnderManuellOppfolging
                +"true"
            }
            option {
                value = "false"
                selected = !defaults.erUnderManuellOppfolging
                +"false"
            }
        }
    }

    div("field") {
        label { +"Oppfolgingsperioder (JSON array)" }
        textArea(rows = "10", cols = "80") {
            name = "oppfolgingsperioder"
            required = true
            +defaults.oppfolgingsperioderJson
        }
    }
}

private fun FORM.veilederField(options: List<nom.NomVeilederOption>, selectedValue: String) {
    div("field") {
        label { +"Veilederident" }
        select {
            name = "veilederIdent"
            required = true
            options.forEachIndexed { index, option ->
                option {
                    value = option.navident
                    selected = selectedValue == option.navident || (selectedValue.isBlank() && index == 0)
                    +option.label
                }
            }
        }
    }
}

private fun HEAD.veilarboppfolgingFormStyles() {
    style {
        unsafe {
            +"""
            body { font-family: sans-serif; margin: 2rem; max-width: 60rem; }
            form { border: 1px solid #d8d8d8; padding: 1rem; border-radius: 6px; }
            .field { margin-bottom: 0.75rem; display: flex; flex-direction: column; gap: 0.25rem; }
            input, select, textarea { max-width: 52rem; padding: 0.4rem; font-family: monospace; }
            button { padding: 0.5rem 0.8rem; }
            """.trimIndent()
        }
    }
}


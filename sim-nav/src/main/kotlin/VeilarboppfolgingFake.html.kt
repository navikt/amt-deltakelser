import kotlinx.html.*
import sharedui.simNavHeader
import sharedui.simNavCrudPageStyles
import sharedui.simNavFormPageStyles
import sharedui.simNavHeaderStyles

fun HTML.veilarboppfolgingPage(
    persons: List<VeilarboppfolgingPersonRow>,
    message: String?,
    isError: Boolean,
    newPersonPath: String,
    editPersonPathPrefix: String,
    pdlNamesByFnr: Map<String, String>,
    nomNamesByNavident: Map<String, String>,
) {
    head {
        title("Veilarboppfolging - Simulator")
        simNavHeaderStyles()
        simNavCrudPageStyles()
    }

    body {
        simNavHeader(VEILARBOPPFOLGING_PATH_PREFIX)
        h1 { +"Veilarboppfolging" }
        p { +"Tjeneste som lagrer informasjon om status for arbeidsrette oppfølging for en bruker" }

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
                        th { +"Navn" }
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
                            td {
                                +row.veilederIdent
                                +" "
                                +(nomNamesByNavident[row.veilederIdent]?.takeIf { it.isNotBlank() } ?: "")
                            }
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
        simNavHeaderStyles()
        veilarboppfolgingFormStyles()
    }

    body {
        simNavHeader(VEILARBOPPFOLGING_PATH_PREFIX)
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
        simNavHeaderStyles()
        veilarboppfolgingFormStyles()
    }

    body {
        simNavHeader(VEILARBOPPFOLGING_PATH_PREFIX)
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
        textArea(rows = "10") {
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
    simNavFormPageStyles(fieldSelector = "input, select, textarea", monospaceFields = true)
}


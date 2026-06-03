package tjenester.nav.aooppfolgingskontor

import kotlinx.html.*
import sharedui.simNavCrudPageStyles
import sharedui.simNavFormPageStyles
import sharedui.simNavHeader
import sharedui.simNavHeaderStyles

fun HTML.aoOppfolgingskontorPage(
    rows: List<AoOppfolgingskontorKontorTilhorighetRow>,
    message: String?,
    isError: Boolean,
    newPath: String,
    editPathPrefix: String,
    pdlNamesByPersonident: Map<String, String>,
) {
    head {
        title("Ao-oppfolgingskontor - Simulator")
        simNavHeaderStyles()
        simNavCrudPageStyles()
    }

    body {
        simNavHeader(AO_OPPFOLGINGSKONTOR_PATH_PREFIX)
        h1 { +"Ao-oppfolgingskontor" }
        p { +"Leverer informasjon om oppfølgingskontor for arbeidsrettet oppfølging for brukere" }

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
        simNavHeaderStyles()
        aoOppfolgingskontorFormStyles()
    }

    body {
        simNavHeader(AO_OPPFOLGINGSKONTOR_PATH_PREFIX)
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
        simNavHeaderStyles()
        aoOppfolgingskontorFormStyles()
    }

    body {
        simNavHeader(AO_OPPFOLGINGSKONTOR_PATH_PREFIX)
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
    simNavFormPageStyles(fieldSelector = "input, select")
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


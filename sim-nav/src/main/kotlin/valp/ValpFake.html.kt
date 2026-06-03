package valp

import kotlinx.html.*

fun HTML.valpPage(
    gjennomforings: List<GjennomforingRow>,
    tiltakstyper: List<TiltakstypeRow>,
    message: String?,
    isError: Boolean,
    newGjennomforingPath: String,
    newTiltakstypePath: String,
) {
    head {
        title("Valp - Simulator")
        style {
            unsafe {
                raw(
                    """
                    body { font-family: Arial, sans-serif; margin: 20px; }
                    h1 { color: #333; }
                    h2 { color: #666; margin-top: 30px; border-bottom: 2px solid #ddd; padding-bottom: 10px; display: flex; align-items: center; justify-content: space-between; }
                    .empty { color: #999; font-style: italic; padding: 20px; }
                    .section { margin: 30px 0; }
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
                    .type-badge {
                        display: inline-block;
                        padding: 3px 8px;
                        border-radius: 3px;
                        font-size: 0.85em;
                        font-weight: bold;
                    }
                    .type-enkeltplass { background-color: #fff3cd; color: #856404; }
                    .type-gruppe { background-color: #cfe2ff; color: #084298; }
                    """.trimIndent(),
                )
            }
        }
    }
    body {
        h1 { +"Valp - Simulator" }

        if (message != null) {
            p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                +message
            }
        }

        div(classes = "section") {
            h2 {
                +"Gjennomforinger (${gjennomforings.size})"
                a(classes = "add-button", href = newGjennomforingPath) {
                    title = "Add new gjennomforing"
                    +"+"
                }
            }
            if (gjennomforings.isEmpty()) {
                div(classes = "empty") {
                    +"No gjennomforinger in database"
                }
            } else {
                table {
                    thead {
                        tr {
                            th { +"Type" }
                            th { +"Tiltakskode" }
                            th { +"Arrangor" }
                            th { +"Status" }
                            th { +"Navn" }
                            th { +"Start Dato" }
                            th { +"Slutt Dato" }
                            th { +"ID" }
                        }
                    }
                    tbody {
                        gjennomforings.forEach { row ->
                            tr {
                                td {
                                    span(classes = "type-badge type-${row.type}") {
                                        +row.type
                                    }
                                }
                                td { +row.tiltakskode }
                                td { +row.arrangor }
                                td { +row.status }
                                td { +(row.navn ?: "-") }
                                td { +(row.startDato ?: "-") }
                                td { +(row.sluttDato ?: "-") }
                                td {
                                    span(classes = "id") {
                                        +row.id
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        div(classes = "section") {
            h2 {
                +"Tiltakstyper (${tiltakstyper.size})"
                a(classes = "add-button", href = newTiltakstypePath) {
                    title = "Add new tiltakstype"
                    +"+"
                }
            }
            if (tiltakstyper.isEmpty()) {
                div(classes = "empty") {
                    +"No tiltakstyper in database"
                }
            } else {
                table {
                    thead {
                        tr {
                            th { +"Navn" }
                            th { +"Tiltakskode" }
                            th { +"Innsatsgrupper" }
                            th { +"ID" }
                        }
                    }
                    tbody {
                        tiltakstyper.forEach { row ->
                            tr {
                                td { +row.navn }
                                td { +row.tiltakskode }
                                td {
                                    span(classes = "id") {
                                        +row.innsatsgrupper
                                    }
                                }
                                td {
                                    span(classes = "id") {
                                        +row.id
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun HTML.valpGjennomforingFormPage(
    enkeltplassDefaults: GjennomforingFormDefaults,
    gruppeDefaults: GjennomforingFormDefaults,
    enkeltplassActionPath: String,
    gruppeActionPath: String,
    backPath: String,
) {
    head {
        title("New gjennomforing - Valp")
        formPageStyles()
    }
    body {
        h1 { +"Ny gjennomforing" }
        p {
            a(href = backPath) { +"<- Tilbake" }
        }
        section {
            h2 { +"Gjennomforing enkeltplass" }
            form(action = enkeltplassActionPath, method = FormMethod.post) {
                textField("ID", "id", enkeltplassDefaults.id)
                textField("Tiltakskode", "tiltakskode", enkeltplassDefaults.tiltakskode)
                textField("Arrangor organisasjonsnummer", "arrangorOrganisasjonsnummer", enkeltplassDefaults.arrangorOrganisasjonsnummer)
                textField("Pameldingstype", "pameldingType", enkeltplassDefaults.pameldingType)
                textField("Status", "status", enkeltplassDefaults.status)
                textField("Oppstart", "oppstart", enkeltplassDefaults.oppstart)
                dateTimeField("Opprettet tidspunkt (UTC)", "opprettetTidspunkt", enkeltplassDefaults.opprettetTidspunkt)
                dateTimeField("Oppdatert tidspunkt (UTC)", "oppdatertTidspunkt", enkeltplassDefaults.oppdatertTidspunkt)
                textField("Prisinformasjon (valgfri)", "prisinformasjon", enkeltplassDefaults.prisinformasjon, required = false)
                button(type = ButtonType.submit) { +"Lagre enkeltplass" }
            }
        }

        section {
            h2 { +"Gjennomforing gruppe" }
            form(action = gruppeActionPath, method = FormMethod.post) {
                textField("ID", "id", gruppeDefaults.id)
                textField("Tiltakskode", "tiltakskode", gruppeDefaults.tiltakskode)
                textField("Arrangor organisasjonsnummer", "arrangorOrganisasjonsnummer", gruppeDefaults.arrangorOrganisasjonsnummer)
                textField("Pameldingstype", "pameldingType", gruppeDefaults.pameldingType)
                textField("Status", "status", gruppeDefaults.status)
                textField("Oppstart", "oppstart", gruppeDefaults.oppstart)
                dateTimeField("Opprettet tidspunkt (UTC)", "opprettetTidspunkt", gruppeDefaults.opprettetTidspunkt)
                dateTimeField("Oppdatert tidspunkt (UTC)", "oppdatertTidspunkt", gruppeDefaults.oppdatertTidspunkt)
                textField("Navn", "navn", gruppeDefaults.navn)
                dateField("Startdato", "startDato", gruppeDefaults.startDato)
                dateField("Sluttdato (valgfri)", "sluttDato", gruppeDefaults.sluttDato, required = false)
                dateField(
                    "Tilgjengelig for arrangor fra og med dato (valgfri)",
                    "tilgjengeligForArrangorFraOgMedDato",
                    gruppeDefaults.tilgjengeligForArrangorFraOgMedDato,
                    required = false,
                )
                booleanField("Apent for pamelding", "apentForPamelding", gruppeDefaults.apentForPamelding)
                textField("Antall plasser", "antallPlasser", gruppeDefaults.antallPlasser, type = InputType.number)
                textField("Deltidsprosent", "deltidsprosent", gruppeDefaults.deltidsprosent, type = InputType.number)
                textField("Oppmotested (valgfri)", "oppmoteSted", gruppeDefaults.oppmoteSted, required = false)
                button(type = ButtonType.submit) { +"Lagre gruppe" }
            }
        }
    }
}

fun HTML.valpTiltakstypeFormPage(
    defaults: TiltakstypeFormDefaults,
    actionPath: String,
    backPath: String,
) {
    head {
        title("New tiltakstype - Valp")
        formPageStyles()
    }
    body {
        h1 { +"Ny tiltakstype" }
        p {
            a(href = backPath) { +"<- Tilbake" }
        }
        form(action = actionPath, method = FormMethod.post) {
            textField("ID", "id", defaults.id)
            textField("Navn", "navn", defaults.navn)
            textField("Tiltakskode", "tiltakskode", defaults.tiltakskode)
            textField("Innsatsgrupper (JSON)", "innsatsgrupper", defaults.innsatsgrupper)
            button(type = ButtonType.submit) { +"Lagre tiltakstype" }
        }
    }
}

private fun HEAD.formPageStyles() {
    style {
        unsafe {
            +"""
            body { font-family: sans-serif; margin: 2rem; max-width: 60rem; }
            form { border: 1px solid #d8d8d8; padding: 1rem; border-radius: 6px; }
            .field { margin-bottom: 0.75rem; display: flex; flex-direction: column; gap: 0.25rem; }
            input, select { max-width: 32rem; padding: 0.4rem; }
            button { padding: 0.5rem 0.8rem; }
            """.trimIndent()
        }
    }
}

private fun FORM.textField(
    labelText: String,
    name: String,
    value: String,
    required: Boolean = true,
    type: InputType = InputType.text,
) {
    div("field") {
        label { +labelText }
        input(type = type, name = name) {
            this.value = value
            this.required = required
        }
    }
}

private fun FORM.dateField(
    labelText: String,
    name: String,
    value: String,
    required: Boolean = true,
) {
    textField(labelText, name, value, required = required, type = InputType.date)
}

private fun FORM.dateTimeField(
    labelText: String,
    name: String,
    value: String,
    required: Boolean = true,
) {
    textField(labelText, name, value, required = required, type = InputType.dateTimeLocal)
}

private fun FORM.enumField(
    labelText: String,
    name: String,
    options: List<String>,
    selectedValue: String,
) {
    div("field") {
        label { +labelText }
        select {
            this.name = name
            required = true
            options.forEach { value ->
                option {
                    this.value = value
                    selected = value == selectedValue
                    +value
                }
            }
        }
    }
}

private fun FORM.booleanField(labelText: String, name: String, selectedValue: String) {
    enumField(labelText, name, listOf("true", "false"), selectedValue)
}


package valp

import kotlinx.html.*
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DATE_TIME_INPUT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

fun HTML.valpPage(
    gjennomforings: List<GjennomforingRow>,
    tiltakstyper: List<TiltakstypeRow>,
    message: String?,
    isError: Boolean,
    newGjennomforingPath: String,
    editGjennomforingPathPrefix: String,
    newTiltakstypePath: String,
    editTiltakstypePathPrefix: String,
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
                            th { +"Actions" }
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
                                    a(href = "$editGjennomforingPathPrefix/${row.id}/edit") { +"Edit" }
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
                            th { +"Actions" }
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
                                    a(href = "$editTiltakstypePathPrefix/${row.id}/edit") { +"Edit" }
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
    arrangorOptions: List<Pair<String, String>>,
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
                textField("ID", "id", enkeltplassDefaults.id.toString())
                enumField("Tiltakskode", "tiltakskode", Tiltakskode.entries.map { it.name }, enkeltplassDefaults.tiltakskode.name)
                arrangorField("Arrangor", "arrangorOrganisasjonsnummer", arrangorOptions, enkeltplassDefaults.arrangorOrganisasjonsnummer)
                enumField("Pameldingstype", "pameldingType", GjennomforingPameldingType.entries.map { it.name }, enkeltplassDefaults.pameldingType.name)
                enumField("Status", "status", GjennomforingStatusType.entries.map { it.name }, enkeltplassDefaults.status.name)
                enumField("Oppstart", "oppstart", Oppstartstype.entries.map { it.name }, enkeltplassDefaults.oppstart.name)
                dateTimeField("Opprettet tidspunkt (UTC)", "opprettetTidspunkt", enkeltplassDefaults.opprettetTidspunkt)
                dateTimeField("Oppdatert tidspunkt (UTC)", "oppdatertTidspunkt", enkeltplassDefaults.oppdatertTidspunkt)
                textField("Prisinformasjon (valgfri)", "prisinformasjon", enkeltplassDefaults.prisinformasjon.orEmpty(), required = false)
                button(type = ButtonType.submit) { +"Lagre enkeltplass" }
            }
        }

        section {
            h2 { +"Gjennomforing gruppe" }
            form(action = gruppeActionPath, method = FormMethod.post) {
                textField("ID", "id", gruppeDefaults.id.toString())
                enumField("Tiltakskode", "tiltakskode", Tiltakskode.entries.map { it.name }, gruppeDefaults.tiltakskode.name)
                arrangorField("Arrangor", "arrangorOrganisasjonsnummer", arrangorOptions, gruppeDefaults.arrangorOrganisasjonsnummer)
                enumField("Pameldingstype", "pameldingType", GjennomforingPameldingType.entries.map { it.name }, gruppeDefaults.pameldingType.name)
                enumField("Status", "status", GjennomforingStatusType.entries.map { it.name }, gruppeDefaults.status.name)
                enumField("Oppstart", "oppstart", Oppstartstype.entries.map { it.name }, gruppeDefaults.oppstart.name)
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
                textField("Antall plasser", "antallPlasser", gruppeDefaults.antallPlasser.toString(), type = InputType.number)
                textField("Deltidsprosent", "deltidsprosent", gruppeDefaults.deltidsprosent.toString(), type = InputType.number)
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
            textField("ID", "id", defaults.id.toString())
            textField("Navn", "navn", defaults.navn)
            enumField("Tiltakskode", "tiltakskode", Tiltakskode.entries.map { it.name }, defaults.tiltakskode.name)
            multiEnumField(
                labelText = "Innsatsgrupper",
                name = "innsatsgrupper",
                options = InnsatsgruppeV2.entries.map { it.name },
                selectedValues = defaults.innsatsgrupper.map { it.name }.toSet(),
            )
            button(type = ButtonType.submit) { +"Lagre tiltakstype" }
        }
    }
}

fun HTML.valpGjennomforingEditFormPage(
    id: java.util.UUID,
    type: String,
    defaults: GjennomforingFormDefaults,
    actionPath: String,
    arrangorOptions: List<Pair<String, String>>,
    backPath: String,
) {
    head {
        title("Edit gjennomforing - Valp")
        formPageStyles()
    }
    body {
        h1 { +"Rediger gjennomforing" }
        p {
            a(href = backPath) { +"<- Tilbake" }
        }
        p { +"ID: $id" }
        p { +"Oppdatert tidspunkt settes automatisk ved lagring." }

        form(action = actionPath, method = FormMethod.post) {
            enumField("Tiltakskode", "tiltakskode", Tiltakskode.entries.map { it.name }, defaults.tiltakskode.name)
            arrangorField("Arrangor", "arrangorOrganisasjonsnummer", arrangorOptions, defaults.arrangorOrganisasjonsnummer)
            enumField("Pameldingstype", "pameldingType", GjennomforingPameldingType.entries.map { it.name }, defaults.pameldingType.name)
            enumField("Status", "status", GjennomforingStatusType.entries.map { it.name }, defaults.status.name)
            enumField("Oppstart", "oppstart", Oppstartstype.entries.map { it.name }, defaults.oppstart.name)
            dateTimeField("Opprettet tidspunkt (UTC)", "opprettetTidspunkt", defaults.opprettetTidspunkt)
            textField("Prisinformasjon (valgfri)", "prisinformasjon", defaults.prisinformasjon.orEmpty(), required = false)

            if (type == "gruppe") {
                textField("Navn", "navn", defaults.navn)
                dateField("Startdato", "startDato", defaults.startDato)
                dateField("Sluttdato (valgfri)", "sluttDato", defaults.sluttDato, required = false)
                dateField(
                    "Tilgjengelig for arrangor fra og med dato (valgfri)",
                    "tilgjengeligForArrangorFraOgMedDato",
                    defaults.tilgjengeligForArrangorFraOgMedDato,
                    required = false,
                )
                booleanField("Apent for pamelding", "apentForPamelding", defaults.apentForPamelding)
                textField("Antall plasser", "antallPlasser", defaults.antallPlasser.toString(), type = InputType.number)
                textField("Deltidsprosent", "deltidsprosent", defaults.deltidsprosent.toString(), type = InputType.number)
                textField("Oppmotested (valgfri)", "oppmoteSted", defaults.oppmoteSted, required = false)
            }

            button(type = ButtonType.submit) { +"Lagre endringer" }
        }
    }
}

fun HTML.valpTiltakstypeEditFormPage(
    id: java.util.UUID,
    defaults: TiltakstypeFormDefaults,
    actionPath: String,
    backPath: String,
) {
    head {
        title("Edit tiltakstype - Valp")
        formPageStyles()
    }
    body {
        h1 { +"Rediger tiltakstype" }
        p {
            a(href = backPath) { +"<- Tilbake" }
        }
        p { +"ID: $id" }

        form(action = actionPath, method = FormMethod.post) {
            textField("Navn", "navn", defaults.navn)
            enumField("Tiltakskode", "tiltakskode", Tiltakskode.entries.map { it.name }, defaults.tiltakskode.name)
            multiEnumField(
                labelText = "Innsatsgrupper",
                name = "innsatsgrupper",
                options = InnsatsgruppeV2.entries.map { it.name },
                selectedValues = defaults.innsatsgrupper.map { it.name }.toSet(),
            )
            button(type = ButtonType.submit) { +"Lagre endringer" }
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
    value: LocalDateTime,
    required: Boolean = true,
) {
    textField(labelText, name, value.format(DATE_TIME_INPUT_FORMATTER), required = required, type = InputType.dateTimeLocal)
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

private fun FORM.booleanField(labelText: String, name: String, selectedValue: Boolean) {
    enumField(labelText, name, listOf("true", "false"), selectedValue.toString())
}

private fun FORM.arrangorField(
    labelText: String,
    name: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
) {
    div("field") {
        label { +labelText }
        select {
            this.name = name
            required = true
            options.forEach { (orgnr, navn) ->
                option {
                    value = orgnr
                    selected = orgnr == selectedValue
                    +"$orgnr - $navn"
                }
            }
        }
    }
}

private fun FORM.multiEnumField(
    labelText: String,
    name: String,
    options: List<String>,
    selectedValues: Set<String>,
) {
    div("field") {
        label { +labelText }
        select {
            this.name = name
            multiple = true
            attributes["size"] = options.size.toString()
            options.forEach { value ->
                option {
                    this.value = value
                    selected = selectedValues.contains(value)
                    +value
                }
            }
        }
    }
}


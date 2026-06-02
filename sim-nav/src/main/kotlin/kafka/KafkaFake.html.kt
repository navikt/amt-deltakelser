package kafka

import kotlinx.html.*
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import java.time.format.DateTimeFormatter

private val DATE_TIME_INPUT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

fun HTML.kafkaPublishPage(
    message: String?,
    isError: Boolean,
    enkeltplassDefaults: GjennomforingV2KafkaPayload.Enkeltplass,
    gruppeDefaults: GjennomforingV2KafkaPayload.Gruppe,
    tiltakstypeDefaults: TiltakstypeDto,
    enkeltplassPath: String,
    gruppePath: String,
    tiltakstypePath: String,
    arrangorOptions: List<Pair<String, String>>,
) {
    head {
        title("Sim-nav Kafka publisher")
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        style {
            unsafe {
                +"""
                body { font-family: sans-serif; margin: 2rem; max-width: 60rem; }
                form { border: 1px solid #d8d8d8; padding: 1rem; border-radius: 6px; margin-bottom: 1rem; }
                .field { margin-bottom: 0.75rem; display: flex; flex-direction: column; gap: 0.25rem; }
                .message { padding: 0.75rem; border-radius: 6px; margin-bottom: 1rem; }
                .message--ok { background: #ebfbee; border: 1px solid #b2f2bb; }
                .message--error { background: #fff5f5; border: 1px solid #ffc9c9; }
                input, select { max-width: 32rem; padding: 0.4rem; }
                button { padding: 0.5rem 0.8rem; }
                """.trimIndent()
            }
        }
    }
    body {
        main {
            h1 { +"Sim Valp (?)" }
            p { +"Fyll ut skjemaene under for manuell publisering av Kafka-meldinger." }

            if (message != null) {
                p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                    +message
                }
            }

            section {
                h2 { +"Gjennomføring enkeltplass" }
                gjennomforingEnkeltplassForm(enkeltplassDefaults, enkeltplassPath, arrangorOptions)
            }

            section {
                h2 { +"Gjennomføring gruppe" }
                gjennomforingGruppeForm(gruppeDefaults, gruppePath, arrangorOptions)
            }

            section {
                h2 { +"Tiltakstype" }
                tiltakstypeForm(tiltakstypeDefaults, tiltakstypePath)
            }
        }
    }
}

private fun FlowContent.gjennomforingEnkeltplassForm(
    defaults: GjennomforingV2KafkaPayload.Enkeltplass,
    actionPath: String,
    arrangorOptions: List<Pair<String, String>>,
) {
    form(action = actionPath, method = FormMethod.post) {
        div("field") {
            label { +"ID" }
            input(type = InputType.text, name = "id") {
                value = defaults.id.toString()
                required = true
            }
        }
        div("field") {
            label { +"Opprettet tidspunkt (UTC)" }
            input(type = InputType.dateTimeLocal, name = "opprettetTidspunkt") {
                value = defaults.opprettetTidspunkt.toLocalDateTime().format(DATE_TIME_INPUT_FORMATTER)
                required = true
            }
        }
        div("field") {
            label { +"Oppdatert tidspunkt (UTC)" }
            input(type = InputType.dateTimeLocal, name = "oppdatertTidspunkt") {
                value = defaults.oppdatertTidspunkt.toLocalDateTime().format(DATE_TIME_INPUT_FORMATTER)
                required = true
            }
        }
        enumField("Tiltakskode", "tiltakskode", Tiltakskode.entries.map { it.name }, defaults.tiltakskode.name)
        div("field") {
            label { +"Arrangør" }
            select {
                name = "arrangorOrganisasjonsnummer"
                required = true
                arrangorOptions.forEach { (orgnr, navn) ->
                    option {
                        value = orgnr
                        selected = orgnr == defaults.arrangor.organisasjonsnummer
                        +"$orgnr – $navn"
                    }
                }
            }
        }
        enumField("Pameldingstype", "pameldingType", GjennomforingPameldingType.entries.map { it.name }, defaults.pameldingType.name)
        enumField("Status", "status", GjennomforingStatusType.entries.map { it.name }, defaults.status.name)
        enumField("Oppstart", "oppstart", Oppstartstype.entries.map { it.name }, defaults.oppstart.name)
        div("field") {
            label { +"Prisinformasjon (valgfri)" }
            input(type = InputType.text, name = "prisinformasjon") {
                value = defaults.prisinformasjon.orEmpty()
            }
        }
        button(type = ButtonType.submit) { +"Publiser gjennomforing" }
    }
}

private fun FlowContent.gjennomforingGruppeForm(
    defaults: GjennomforingV2KafkaPayload.Gruppe,
    actionPath: String,
    arrangorOptions: List<Pair<String, String>>,
) {
    form(action = actionPath, method = FormMethod.post) {
        div("field") {
            label { +"ID" }
            input(type = InputType.text, name = "id") {
                value = defaults.id.toString()
                required = true
            }
        }
        div("field") {
            label { +"Opprettet tidspunkt (UTC)" }
            input(type = InputType.dateTimeLocal, name = "opprettetTidspunkt") {
                value = defaults.opprettetTidspunkt.toLocalDateTime().format(DATE_TIME_INPUT_FORMATTER)
                required = true
            }
        }
        div("field") {
            label { +"Oppdatert tidspunkt (UTC)" }
            input(type = InputType.dateTimeLocal, name = "oppdatertTidspunkt") {
                value = defaults.oppdatertTidspunkt.toLocalDateTime().format(DATE_TIME_INPUT_FORMATTER)
                required = true
            }
        }
        enumField("Tiltakskode", "tiltakskode", Tiltakskode.entries.map { it.name }, defaults.tiltakskode.name)
        div("field") {
            label { +"Arrangør" }
            select {
                name = "arrangorOrganisasjonsnummer"
                required = true
                arrangorOptions.forEach { (orgnr, navn) ->
                    option {
                        value = orgnr
                        selected = orgnr == defaults.arrangor.organisasjonsnummer
                        +"$orgnr – $navn"
                    }
                }
            }
        }
        enumField("Pameldingstype", "pameldingType", GjennomforingPameldingType.entries.map { it.name }, defaults.pameldingType.name)
        enumField("Status", "status", GjennomforingStatusType.entries.map { it.name }, defaults.status.name)
        enumField("Oppstart", "oppstart", Oppstartstype.entries.map { it.name }, defaults.oppstart.name)
        div("field") {
            label { +"Navn" }
            input(type = InputType.text, name = "navn") {
                value = defaults.navn
                required = true
            }
        }
        div("field") {
            label { +"Startdato" }
            input(type = InputType.date, name = "startDato") {
                value = defaults.startDato.toString()
                required = true
            }
        }
        div("field") {
            label { +"Sluttdato (valgfri)" }
            input(type = InputType.date, name = "sluttDato") {
                value = defaults.sluttDato?.toString().orEmpty()
            }
        }
        div("field") {
            label { +"Tilgjengelig for arrangor fra og med dato (valgfri)" }
            input(type = InputType.date, name = "tilgjengeligForArrangorFraOgMedDato") {
                value = defaults.tilgjengeligForArrangorFraOgMedDato?.toString().orEmpty()
            }
        }
        booleanField("Apent for pamelding", "apentForPamelding", defaults.apentForPamelding)
        div("field") {
            label { +"Antall plasser" }
            input(type = InputType.number, name = "antallPlasser") {
                value = defaults.antallPlasser.toString()
                min = "0"
                required = true
            }
        }
        div("field") {
            label { +"Deltidsprosent" }
            input(type = InputType.number, name = "deltidsprosent") {
                value = defaults.deltidsprosent.toString()
                step = "0.1"
                min = "0"
                required = true
            }
        }
        div("field") {
            label { +"Oppmotested (valgfri)" }
            input(type = InputType.text, name = "oppmoteSted") {
                value = defaults.oppmoteSted.orEmpty()
            }
        }
        button(type = ButtonType.submit) { +"Publiser gruppe-gjennomforing" }
    }
}

private fun FlowContent.tiltakstypeForm(defaults: TiltakstypeDto, actionPath: String) {
    form(action = actionPath, method = FormMethod.post) {
        div("field") {
            label { +"ID" }
            input(type = InputType.text, name = "id") {
                value = defaults.id.toString()
                required = true
            }
        }
        div("field") {
            label { +"Navn" }
            input(type = InputType.text, name = "navn") {
                value = defaults.navn
                required = true
            }
        }
        enumField("Tiltakskode", "tiltakskode", Tiltakskode.entries.map { it.name }, defaults.tiltakskode.name)
        div("field") {
            label { +"Innsatsgrupper" }
            select {
                name = "innsatsgrupper"
                multiple = true
                attributes["size"] = InnsatsgruppeV2.entries.size.toString()
                InnsatsgruppeV2.entries.forEach { innsatsgruppe ->
                    option {
                        value = innsatsgruppe.name
                        selected = defaults.innsatsgrupper.contains(innsatsgruppe)
                        +innsatsgruppe.name
                    }
                }
            }
        }
        button(type = ButtonType.submit) { +"Publiser tiltakstype" }
    }
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

private fun FORM.booleanField(
    labelText: String,
    name: String,
    selectedValue: Boolean,
) {
    enumField(labelText, name, listOf(true.toString(), false.toString()), selectedValue.toString())
}


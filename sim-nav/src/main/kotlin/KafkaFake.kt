import io.ktor.http.HttpStatusCode
import io.ktor.server.html.respondHtml
import io.ktor.server.request.receiveParameters
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.html.FormMethod
import kotlinx.html.FlowContent
import kotlinx.html.HTML
import kotlinx.html.InputType
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.head
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.main
import kotlinx.html.meta
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.section
import kotlinx.html.select
import kotlinx.html.style
import kotlinx.html.title
import kotlinx.html.unsafe
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val KAFKA_ENKELTPLASS_GJENNOMFORING_PATH = "/kafka/gjennomforing/enkeltplass"
private const val KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH = "/kafka/tiltakstype/enkeltplass-amo"
private const val KAFKA_PAGE_PATH = "/kafka"

private val DATE_TIME_INPUT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

fun Route.kafkaFakeRoutes(
    kafkaPublisher: KafkaPublisher,
) {
    get(KAFKA_PAGE_PATH) {
        call.respondHtml(HttpStatusCode.OK) {
            kafkaPublishPage(
                message = null,
                isError = false,
                gjennomforingDefaults = kafkaPublisher.defaultGjennomforingEnkeltplass(),
                tiltakstypeDefaults = kafkaPublisher.defaultTiltakstypeEnkeltplassArbeidsmarkedsopplaering(),
            )
        }
    }

    route(KAFKA_ENKELTPLASS_GJENNOMFORING_PATH) {
        post {
            try {
                val payload = call.receiveParameters().toGjennomforingPayload()
                kafkaPublisher.publishGjennomforingEnkeltplass(payload)
                call.respondHtml(HttpStatusCode.Accepted) {
                    kafkaPublishPage(
                        message = "Publiserte gjennomforing med id ${payload.id}",
                        isError = false,
                        gjennomforingDefaults = payload,
                        tiltakstypeDefaults = kafkaPublisher.defaultTiltakstypeEnkeltplassArbeidsmarkedsopplaering(),
                    )
                }
            } catch (exception: Exception) {
                call.respondHtml(HttpStatusCode.BadRequest) {
                    kafkaPublishPage(
                        message = "Kunne ikke publisere gjennomforing: ${exception.message ?: "ukjent feil"}",
                        isError = true,
                        gjennomforingDefaults = kafkaPublisher.defaultGjennomforingEnkeltplass(),
                        tiltakstypeDefaults = kafkaPublisher.defaultTiltakstypeEnkeltplassArbeidsmarkedsopplaering(),
                    )
                }
            }
        }
    }

    route(KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH) {
        post {
            try {
                val payload = call.receiveParameters().toTiltakstypePayload()
                kafkaPublisher.publishTiltakstypeEnkeltplassArbeidsmarkedsopplaering(payload)
                call.respondHtml(HttpStatusCode.Accepted) {
                    kafkaPublishPage(
                        message = "Publiserte tiltakstype med id ${payload.id}",
                        isError = false,
                        gjennomforingDefaults = kafkaPublisher.defaultGjennomforingEnkeltplass(),
                        tiltakstypeDefaults = payload,
                    )
                }
            } catch (exception: Exception) {
                call.respondHtml(HttpStatusCode.BadRequest) {
                    kafkaPublishPage(
                        message = "Kunne ikke publisere tiltakstype: ${exception.message ?: "ukjent feil"}",
                        isError = true,
                        gjennomforingDefaults = kafkaPublisher.defaultGjennomforingEnkeltplass(),
                        tiltakstypeDefaults = kafkaPublisher.defaultTiltakstypeEnkeltplassArbeidsmarkedsopplaering(),
                    )
                }
            }
        }
    }
}

private fun io.ktor.http.Parameters.toGjennomforingPayload(): GjennomforingV2KafkaPayload.Enkeltplass {
    return GjennomforingV2KafkaPayload.Enkeltplass(
        id = UUID.fromString(required("id")),
        opprettetTidspunkt = required("opprettetTidspunkt").toOffsetDateTimeUtc(),
        oppdatertTidspunkt = required("oppdatertTidspunkt").toOffsetDateTimeUtc(),
        tiltakskode = enumValueOf(required("tiltakskode")),
        arrangor = GjennomforingV2KafkaPayload.Arrangor(required("arrangorOrganisasjonsnummer")),
        pameldingType = enumValueOf(required("pameldingType")),
        status = enumValueOf(required("status")),
        oppstart = enumValueOf(required("oppstart")),
        prisinformasjon = optional("prisinformasjon"),
    )
}

private fun io.ktor.http.Parameters.toTiltakstypePayload(): TiltakstypeDto {
    return TiltakstypeDto(
        id = UUID.fromString(required("id")),
        navn = required("navn"),
        tiltakskode = enumValueOf(required("tiltakskode")),
        innsatsgrupper = getAll("innsatsgrupper")
            ?.map { enumValueOf<InnsatsgruppeV2>(it) }
            ?.toSet()
            ?: emptySet(),
        deltakerRegistreringInnhold = null,
    )
}

private fun io.ktor.http.Parameters.required(name: String): String {
    return get(name)?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Mangler felt '$name'")
}

private fun io.ktor.http.Parameters.optional(name: String): String? = get(name)?.takeIf { it.isNotBlank() }

private fun String.toOffsetDateTimeUtc() = LocalDateTime.parse(this).atOffset(ZoneOffset.UTC)

private fun HTML.kafkaPublishPage(
    message: String?,
    isError: Boolean,
    gjennomforingDefaults: GjennomforingV2KafkaPayload.Enkeltplass,
    tiltakstypeDefaults: TiltakstypeDto,
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
            h1 { +"Sim-nav Kafka publisher" }
            p { +"Fyll ut skjemaene under for manuell publisering av Kafka-meldinger." }

            if (message != null) {
                p(classes = "message ${if (isError) "message--error" else "message--ok"}") {
                    +message
                }
            }

            section {
                h2 { +"Gjennomforing enkeltplass" }
                gjennomforingForm(gjennomforingDefaults)
            }

            section {
                h2 { +"Tiltakstype enkeltplass AMO" }
                tiltakstypeForm(tiltakstypeDefaults)
            }
        }
    }
}

private fun FlowContent.gjennomforingForm(defaults: GjennomforingV2KafkaPayload.Enkeltplass) {
    form(action = KAFKA_ENKELTPLASS_GJENNOMFORING_PATH, method = FormMethod.post) {
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
            label { +"Arrangor organisasjonsnummer" }
            input(type = InputType.text, name = "arrangorOrganisasjonsnummer") {
                value = defaults.arrangor.organisasjonsnummer
                required = true
                pattern = "[0-9]{9}"
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
        button(type = kotlinx.html.ButtonType.submit) { +"Publiser gjennomforing" }
    }
}

private fun FlowContent.tiltakstypeForm(defaults: TiltakstypeDto) {
    form(action = KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH, method = FormMethod.post) {
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
        button(type = kotlinx.html.ButtonType.submit) { +"Publiser tiltakstype" }
    }
}

private fun kotlinx.html.FORM.enumField(
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


package kafka

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import no.nav.amt.lib.models.deltakerliste.kafka.GjennomforingV2KafkaPayload
import no.nav.amt.lib.models.deltakerliste.tiltakstype.kafka.TiltakstypeDto
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*

private const val KAFKA_ENKELTPLASS_GJENNOMFORING_PATH = "/kafka/gjennomforing/enkeltplass"
private const val KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH = "/kafka/tiltakstype/enkeltplass-amo"
private const val KAFKA_PAGE_PATH = "/kafka"

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
                gjennomforingPath = KAFKA_ENKELTPLASS_GJENNOMFORING_PATH,
                tiltakstypePath = KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH,
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
                        gjennomforingPath = KAFKA_ENKELTPLASS_GJENNOMFORING_PATH,
                        tiltakstypePath = KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH,
                    )
                }
            } catch (exception: Exception) {
                call.respondHtml(HttpStatusCode.BadRequest) {
                    kafkaPublishPage(
                        message = "Kunne ikke publisere gjennomforing: ${exception.message ?: "ukjent feil"}",
                        isError = true,
                        gjennomforingDefaults = kafkaPublisher.defaultGjennomforingEnkeltplass(),
                        tiltakstypeDefaults = kafkaPublisher.defaultTiltakstypeEnkeltplassArbeidsmarkedsopplaering(),
                        gjennomforingPath = KAFKA_ENKELTPLASS_GJENNOMFORING_PATH,
                        tiltakstypePath = KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH,
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
                        gjennomforingPath = KAFKA_ENKELTPLASS_GJENNOMFORING_PATH,
                        tiltakstypePath = KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH,
                    )
                }
            } catch (exception: Exception) {
                call.respondHtml(HttpStatusCode.BadRequest) {
                    kafkaPublishPage(
                        message = "Kunne ikke publisere tiltakstype: ${exception.message ?: "ukjent feil"}",
                        isError = true,
                        gjennomforingDefaults = kafkaPublisher.defaultGjennomforingEnkeltplass(),
                        tiltakstypeDefaults = kafkaPublisher.defaultTiltakstypeEnkeltplassArbeidsmarkedsopplaering(),
                        gjennomforingPath = KAFKA_ENKELTPLASS_GJENNOMFORING_PATH,
                        tiltakstypePath = KAFKA_ENKELTPLASS_TILTAKSTYPE_PATH,
                    )
                }
            }
        }
    }
}

private fun Parameters.toGjennomforingPayload(): GjennomforingV2KafkaPayload.Enkeltplass {
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

private fun Parameters.toTiltakstypePayload(): TiltakstypeDto {
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

private fun Parameters.required(name: String): String {
    return get(name)?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Mangler felt '$name'")
}

private fun Parameters.optional(name: String): String? = get(name)?.takeIf { it.isNotBlank() }

private fun String.toOffsetDateTimeUtc() = LocalDateTime.parse(this).atOffset(ZoneOffset.UTC)



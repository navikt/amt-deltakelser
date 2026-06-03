package valp

import brreg.BronnoysundSimulator
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kafka.KafkaPublisher
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val VALP_PATH_PREFIX = "/valp"

private const val VALP_GJENNOMFORING_NEW_PATH = "$VALP_PATH_PREFIX/gjennomforing/new"
private const val VALP_GJENNOMFORING_ENKELTPLASS_CREATE_PATH = "$VALP_PATH_PREFIX/gjennomforing/enkeltplass"
private const val VALP_GJENNOMFORING_GRUPPE_CREATE_PATH = "$VALP_PATH_PREFIX/gjennomforing/gruppe"
private const val VALP_TILTAKSTYPE_NEW_PATH = "$VALP_PATH_PREFIX/tiltakstype/new"
private const val VALP_TILTAKSTYPE_CREATE_PATH = "$VALP_PATH_PREFIX/tiltakstype"

fun Route.valpFakeRoutes(
    bronnoysundSimulator: BronnoysundSimulator,
    kafkaPublisher: KafkaPublisher,
) {
    get(VALP_PATH_PREFIX) {
        call.respondValpOverview(
            message = call.request.queryParameters["message"],
            isError = call.request.queryParameters["isError"].toBoolean(),
        )
    }

    get(VALP_GJENNOMFORING_NEW_PATH) {
        call.respondHtml {
            valpGjennomforingFormPage(
                enkeltplassDefaults = defaultGjennomforingEnkeltplassFormDefaults(),
                gruppeDefaults = defaultGjennomforingGruppeFormDefaults(),
                enkeltplassActionPath = VALP_GJENNOMFORING_ENKELTPLASS_CREATE_PATH,
                gruppeActionPath = VALP_GJENNOMFORING_GRUPPE_CREATE_PATH,
                arrangorOptions = bronnoysundSimulator.allEnheter(),
                backPath = VALP_PATH_PREFIX,
            )
        }
    }

    get(VALP_TILTAKSTYPE_NEW_PATH) {
        call.respondHtml {
            valpTiltakstypeFormPage(
                defaults = defaultTiltakstypeFormDefaults(),
                actionPath = VALP_TILTAKSTYPE_CREATE_PATH,
                backPath = VALP_PATH_PREFIX,
            )
        }
    }

    post(VALP_GJENNOMFORING_ENKELTPLASS_CREATE_PATH) {
        try {
            val form = call.receiveParameters().toGjennomforingEnkeltplassFormInput()
            insertGjennomforing(form)
            kafkaPublisher.publishGjennomforingEnkeltplass(form.toKafkaEnkeltplassPayload())

            call.redirectToValp("Created enkeltplass-gjennomforing ${form.id}")
        } catch (exception: Exception) {
            call.redirectToValp(
                message = "Could not create/publish enkeltplass-gjennomforing: ${exception.message ?: "unknown error"}",
                isError = true,
            )
        }
    }

    post(VALP_GJENNOMFORING_GRUPPE_CREATE_PATH) {
        try {
            val form = call.receiveParameters().toGjennomforingGruppeFormInput()
            insertGjennomforing(form)
            kafkaPublisher.publishGjennomforingGruppe(form.toKafkaGruppePayload())

            call.redirectToValp("Created gruppe-gjennomforing ${form.id}")
        } catch (exception: Exception) {
            call.redirectToValp(
                message = "Could not create/publish gruppe-gjennomforing: ${exception.message ?: "unknown error"}",
                isError = true,
            )
        }
    }

    post(VALP_TILTAKSTYPE_CREATE_PATH) {
        try {
            val form = call.receiveParameters().toTiltakstypeFormInput()
            insertTiltakstype(form)
            kafkaPublisher.publishTiltakstypeEnkeltplassArbeidsmarkedsopplaering(form.toKafkaTiltakstypePayload())

            call.redirectToValp("Created tiltakstype ${form.id}")
        } catch (exception: Exception) {
            call.redirectToValp(
                message = "Could not create/publish tiltakstype: ${exception.message ?: "unknown error"}",
                isError = true,
            )
        }
    }
}

private suspend fun ApplicationCall.respondValpOverview(
    message: String?,
    isError: Boolean,
) {
    val gjennomforings = fetchGjennomforinger()
    val tiltakstyper = fetchTiltakstyper()

    respondHtml {
        valpPage(
            gjennomforings = gjennomforings,
            tiltakstyper = tiltakstyper,
            message = message,
            isError = isError,
            newGjennomforingPath = VALP_GJENNOMFORING_NEW_PATH,
            newTiltakstypePath = VALP_TILTAKSTYPE_NEW_PATH,
        )
    }
}

private suspend fun ApplicationCall.redirectToValp(message: String, isError: Boolean = false) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$VALP_PATH_PREFIX?message=$encodedMessage&isError=$isError")
}



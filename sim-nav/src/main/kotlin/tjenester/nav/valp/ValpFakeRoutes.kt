package tjenester.nav.valp

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kafka.KafkaPublisher
import tjenester.brreg.BronnoysundSimulator
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*

const val VALP_PATH_PREFIX = "/valp"

private const val VALP_GJENNOMFORING_NEW_PATH = "$VALP_PATH_PREFIX/gjennomforing/new"
private const val VALP_GJENNOMFORING_ENKELTPLASS_CREATE_PATH = "$VALP_PATH_PREFIX/gjennomforing/enkeltplass"
private const val VALP_GJENNOMFORING_GRUPPE_CREATE_PATH = "$VALP_PATH_PREFIX/gjennomforing/gruppe"
private const val VALP_GJENNOMFORING_EDIT_PATH = "$VALP_PATH_PREFIX/gjennomforing/{id}/edit"
private const val VALP_TILTAKSTYPE_NEW_PATH = "$VALP_PATH_PREFIX/tiltakstype/new"
private const val VALP_TILTAKSTYPE_CREATE_PATH = "$VALP_PATH_PREFIX/tiltakstype"
private const val VALP_TILTAKSTYPE_EDIT_PATH = "$VALP_PATH_PREFIX/tiltakstype/{id}/edit"

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

    get(VALP_GJENNOMFORING_EDIT_PATH) {
        val id = call.pathUuidOrRedirect() ?: return@get
        val form = fetchGjennomforingById(id)
        if (form == null) {
            call.redirectToValp("Could not find gjennomforing $id", isError = true)
            return@get
        }

        call.respondHtml {
            valpGjennomforingEditFormPage(
                id = id,
                type = form.type,
                defaults = form.toFormDefaults(),
                actionPath = gjennomforingEditPath(id),
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

    get(VALP_TILTAKSTYPE_EDIT_PATH) {
        val id = call.pathUuidOrRedirect() ?: return@get
        val form = fetchTiltakstypeById(id)
        if (form == null) {
            call.redirectToValp("Could not find tiltakstype $id", isError = true)
            return@get
        }

        call.respondHtml {
            valpTiltakstypeEditFormPage(
                id = id,
                defaults = form.toFormDefaults(),
                actionPath = tiltakstypeEditPath(id),
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

    post(VALP_GJENNOMFORING_EDIT_PATH) {

        val id = call.pathUuidOrRedirect() ?: return@post
        val existing = fetchGjennomforingById(id)
        if (existing == null) {
            call.redirectToValp("Could not find gjennomforing $id", isError = true)
            return@post
        }

        try {
            val form = when (existing.type) {
                "enkeltplass" -> call.receiveParameters().toGjennomforingEnkeltplassEditFormInput(id)
                "gruppe" -> call.receiveParameters().toGjennomforingGruppeEditFormInput(id)
                else -> throw IllegalArgumentException("Unsupported gjennomforing type '${existing.type}'")
            }

            val updated = updateGjennomforing(form)
            if (!updated) {
                call.redirectToValp("Could not update gjennomforing $id", isError = true)
                return@post
            }

            when (form.type) {
                "enkeltplass" -> kafkaPublisher.publishGjennomforingEnkeltplass(form.toKafkaEnkeltplassPayload())
                "gruppe" -> kafkaPublisher.publishGjennomforingGruppe(form.toKafkaGruppePayload())
            }

            call.redirectToValp("Updated ${form.type}-gjennomforing ${form.id}")
        } catch (exception: Exception) {
            call.redirectToValp(
                message = "Could not edit/publish gjennomforing: ${exception.message ?: "unknown error"}",
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

    post(VALP_TILTAKSTYPE_EDIT_PATH) {

        val id = call.pathUuidOrRedirect() ?: return@post
        if (fetchTiltakstypeById(id) == null) {
            call.redirectToValp("Could not find tiltakstype $id", isError = true)
            return@post
        }

        try {
            val form = call.receiveParameters().toTiltakstypeEditFormInput(id)
            val updated = updateTiltakstype(form)
            if (!updated) {
                call.redirectToValp("Could not update tiltakstype $id", isError = true)
                return@post
            }

            kafkaPublisher.publishTiltakstypeEnkeltplassArbeidsmarkedsopplaering(form.toKafkaTiltakstypePayload())
            call.redirectToValp("Updated tiltakstype ${form.id}")
        } catch (exception: Exception) {
            call.redirectToValp(
                message = "Could not edit/publish tiltakstype: ${exception.message ?: "unknown error"}",
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
            editGjennomforingPathPrefix = "$VALP_PATH_PREFIX/gjennomforing",
            newTiltakstypePath = VALP_TILTAKSTYPE_NEW_PATH,
            editTiltakstypePathPrefix = "$VALP_PATH_PREFIX/tiltakstype",
        )
    }
}

private fun gjennomforingEditPath(id: UUID): String = "$VALP_PATH_PREFIX/gjennomforing/$id/edit"

private fun tiltakstypeEditPath(id: UUID): String = "$VALP_PATH_PREFIX/tiltakstype/$id/edit"

private suspend fun ApplicationCall.pathUuidOrRedirect(): UUID? {
    val raw = parameters["id"]
    if (raw.isNullOrBlank()) {
        redirectToValp("Missing path parameter 'id'", isError = true)
        return null
    }

    return try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        redirectToValp("Invalid id '$raw'", isError = true)
        null
    }
}

private suspend fun ApplicationCall.redirectToValp(message: String, isError: Boolean = false) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$VALP_PATH_PREFIX?message=$encodedMessage&isError=$isError")
}



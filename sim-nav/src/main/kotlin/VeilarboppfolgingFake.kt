import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pdl.PdlDataSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

const val VEILARBOPPFOLGING_PATH_PREFIX = "/veilarboppfolging"

private const val VEILARBOPPFOLGING_PERSON_NEW_PATH = "$VEILARBOPPFOLGING_PATH_PREFIX/person/new"
private const val VEILARBOPPFOLGING_PERSON_CREATE_PATH = "$VEILARBOPPFOLGING_PATH_PREFIX/person"
private const val VEILARBOPPFOLGING_PERSON_EDIT_PATH = "$VEILARBOPPFOLGING_PATH_PREFIX/person/{fnr}/edit"
private const val VEILARBOPPFOLGING_PERSON_DELETE_PATH = "$VEILARBOPPFOLGING_PATH_PREFIX/person/{fnr}/delete"

private val veilarboppfolgingObjectMapper = jacksonObjectMapper().findAndRegisterModules()

fun Route.veilarboppfolgingFakeRoutes(pdlDataSource: PdlDataSource) {
    ensureVeilarboppfolgingSeedData(pdlDataSource)

    val fnrOptions = pdlDataSource.allPersons().keys
        .filter { it.matches(Regex("\\d{11}")) }
        .distinct()
        .sorted()
    val validFnrs = fnrOptions.toSet()

    route(VEILARBOPPFOLGING_PATH_PREFIX) {
        get {
            call.respondVeilarboppfolgingOverview(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
            )
        }

        get("person/new") {
            call.respondHtml {
                veilarboppfolgingPersonFormPage(
                    defaults = VeilarboppfolgingPersonFormDefaults(
                        fnr = "",
                        veilederIdent = "Z999999",
                        oppfolgingsperioderJson = "[]",
                        erUnderManuellOppfolging = false,
                    ),
                    actionPath = VEILARBOPPFOLGING_PERSON_CREATE_PATH,
                    backPath = VEILARBOPPFOLGING_PATH_PREFIX,
                    fnrOptions = fnrOptions,
                )
            }
        }

        get("person/{fnr}/edit") {
            val fnr = call.pathFnrOrRedirect(validFnrs) ?: return@get
            val existing = fetchVeilarboppfolgingPersons().firstOrNull { it.fnr == fnr }
            if (existing == null) {
                call.redirectToVeilarboppfolging("Could not find person with fnr $fnr", isError = true)
                return@get
            }

            call.respondHtml {
                veilarboppfolgingPersonEditFormPage(
                    defaults = existing.toFormDefaults(),
                    actionPath = personEditPath(fnr),
                    backPath = VEILARBOPPFOLGING_PATH_PREFIX,
                )
            }
        }

        post("person") {
            try {
                val form = call.receiveParameters().toVeilarboppfolgingPersonFormInput()
                if (!validFnrs.contains(form.fnr)) {
                    call.redirectToVeilarboppfolging("Fnr ${form.fnr} does not exist in PDL fake", isError = true)
                    return@post
                }

                if (fetchVeilarboppfolgingPersonByFnr(form.fnr) != null) {
                    call.redirectToVeilarboppfolging("Person with fnr ${form.fnr} already exists", isError = true)
                    return@post
                }

                insertVeilarboppfolgingPerson(form)
                call.redirectToVeilarboppfolging("Created veilarboppfolging person ${form.fnr}")
            } catch (exception: Exception) {
                call.redirectToVeilarboppfolging(
                    message = "Could not create person: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("person/{fnr}/edit") {
            val fnr = call.pathFnrOrRedirect(validFnrs) ?: return@post
            if (fetchVeilarboppfolgingPersonByFnr(fnr) == null) {
                call.redirectToVeilarboppfolging("Could not find person with fnr $fnr", isError = true)
                return@post
            }

            try {
                val form = call.receiveParameters().toVeilarboppfolgingPersonFormInput(fnr)
                val updated = updateVeilarboppfolgingPerson(form)
                if (!updated) {
                    call.redirectToVeilarboppfolging("Could not update person with fnr $fnr", isError = true)
                    return@post
                }

                call.redirectToVeilarboppfolging("Updated veilarboppfolging person ${form.fnr}")
            } catch (exception: Exception) {
                call.redirectToVeilarboppfolging(
                    message = "Could not edit person: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("person/{fnr}/delete") {
            val fnr = call.pathFnrOrRedirect(validFnrs) ?: return@post
            val deleted = deleteVeilarboppfolgingPerson(fnr)
            if (!deleted) {
                call.redirectToVeilarboppfolging("Could not delete person with fnr $fnr", isError = true)
                return@post
            }

            call.redirectToVeilarboppfolging("Deleted veilarboppfolging person $fnr")
        }

        post("api/v3/hent-veileder") {
            val fnr = readFnrFromBody(readRequestBody(call))
            val person = findPersonForApi(fnr, validFnrs)
            if (person == null) {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"person not found for fnr '$fnr'\"}")
                return@post
            }

            respondJson(
                call,
                HttpStatusCode.OK,
                veilarboppfolgingObjectMapper.writeValueAsString(mapOf("veilederIdent" to person.veilederIdent)),
            )
        }

        post("api/v3/oppfolging/hent-perioder") {
            val fnr = readFnrFromBody(readRequestBody(call))
            val person = findPersonForApi(fnr, validFnrs)
            if (person == null) {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"person not found for fnr '$fnr'\"}")
                return@post
            }

            respondJson(
                call,
                HttpStatusCode.OK,
                veilarboppfolgingObjectMapper.writeValueAsString(person.oppfolgingsperioder),
            )
        }

        post("api/v3/hent-manuell") {
            val fnr = readFnrFromBody(readRequestBody(call))
            val person = findPersonForApi(fnr, validFnrs)
            if (person == null) {
                respondJson(call, HttpStatusCode.NotFound, "{\"error\":\"person not found for fnr '$fnr'\"}")
                return@post
            }

            respondJson(
                call,
                HttpStatusCode.OK,
                veilarboppfolgingObjectMapper.writeValueAsString(
                    mapOf("erUnderManuellOppfolging" to person.erUnderManuellOppfolging),
                ),
            )
        }

        post("api/v3/sak/{oppfolgingsperiodeId}") {
            val oppfolgingsperiodeId = call.parameters["oppfolgingsperiodeId"]
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

            if (oppfolgingsperiodeId == null) {
                respondJson(call, HttpStatusCode.BadRequest, "{\"error\":\"invalid oppfolgingsperiodeId\"}")
            } else {
                val sak = mapOf(
                    "oppfolgingsperiodeId" to oppfolgingsperiodeId,
                    "sakId" to (oppfolgingsperiodeId.mostSignificantBits and Long.MAX_VALUE),
                    "fagsaksystem" to "VEILARBOPPFOLGING",
                )
                respondJson(call, HttpStatusCode.OK, veilarboppfolgingObjectMapper.writeValueAsString(sak))
            }
        }
    }
}

private fun readFnrFromBody(body: String): String {
    val node = veilarboppfolgingObjectMapper.readTree(body)
    return node.path("fnr").asText("")
}

private fun findPersonForApi(
    fnr: String,
    validFnrs: Set<String>,
): VeilarboppfolgingPersonFixture? {
    if (!validFnrs.contains(fnr)) {
        return null
    }

    return fetchVeilarboppfolgingPersonByFnr(fnr)
}

private suspend fun ApplicationCall.respondVeilarboppfolgingOverview(
    message: String?,
    isError: Boolean,
) {
    val persons = fetchVeilarboppfolgingPersons()

    respondHtml {
        veilarboppfolgingPage(
            persons = persons,
            message = message,
            isError = isError,
            newPersonPath = VEILARBOPPFOLGING_PERSON_NEW_PATH,
            editPersonPathPrefix = "$VEILARBOPPFOLGING_PATH_PREFIX/person",
        )
    }
}

private fun personEditPath(fnr: String): String = "$VEILARBOPPFOLGING_PATH_PREFIX/person/$fnr/edit"

private suspend fun ApplicationCall.pathFnrOrRedirect(validFnrs: Set<String>): String? {
    val fnr = parameters["fnr"]
    if (fnr.isNullOrBlank()) {
        redirectToVeilarboppfolging("Missing path parameter 'fnr'", isError = true)
        return null
    }

    if (!fnr.matches(Regex("\\d{11}"))) {
        redirectToVeilarboppfolging("Invalid fnr '$fnr'", isError = true)
        return null
    }

    if (!validFnrs.contains(fnr)) {
        redirectToVeilarboppfolging("Fnr '$fnr' does not exist in PDL fake", isError = true)
        return null
    }

    return fnr
}

private fun Parameters.toVeilarboppfolgingPersonFormInput(fnrOverride: String? = null): VeilarboppfolgingPersonFormInput {
    val fnr = fnrOverride ?: required("fnr")
    val veilederIdent = required("veilederIdent")
    val erUnderManuellOppfolging = required("erUnderManuellOppfolging").toBooleanStrictOrNull()
        ?: error("Field 'erUnderManuellOppfolging' must be true or false")
    val oppfolgingsperioderJson = required("oppfolgingsperioder")
    val oppfolgingsperioder: List<OppfolgingsperiodeFixture> = veilarboppfolgingObjectMapper.readValue(oppfolgingsperioderJson)

    oppfolgingsperioder.forEach {
        runCatching { UUID.fromString(it.uuid) }
            .getOrElse { _ -> error("Invalid oppfolgingsperiode uuid '${it.uuid}'") }
    }

    return VeilarboppfolgingPersonFormInput(
        fnr = fnr,
        veilederIdent = veilederIdent,
        oppfolgingsperioder = oppfolgingsperioder,
        erUnderManuellOppfolging = erUnderManuellOppfolging,
    )
}

private fun Parameters.required(name: String): String {
    return this[name]?.takeIf { it.isNotBlank() }
        ?: error("Missing required form field '$name'")
}

private suspend fun ApplicationCall.redirectToVeilarboppfolging(message: String, isError: Boolean = false) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$VEILARBOPPFOLGING_PATH_PREFIX?message=$encodedMessage&isError=$isError")
}


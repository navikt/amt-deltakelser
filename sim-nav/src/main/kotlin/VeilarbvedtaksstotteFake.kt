import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.amt.lib.models.deltaker.InnsatsgruppeV2
import pdl.PdlDataSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val VEILARBVEDTAKSSTOTTE_PATH_PREFIX = "/veilarbvedtaksstotte"

private const val VEILARBVEDTAKSSTOTTE_PERSON_NEW_PATH = "$VEILARBVEDTAKSSTOTTE_PATH_PREFIX/person/new"
private const val VEILARBVEDTAKSSTOTTE_PERSON_CREATE_PATH = "$VEILARBVEDTAKSSTOTTE_PATH_PREFIX/person"

private val veilarbvedtaksstotteObjectMapper = jacksonObjectMapper()

fun Route.veilarbvedtaksstotteFakeRoutes(pdlDataSource: PdlDataSource) {
    val fnrOptions = buildVeilarbvedtaksstotteFnrOptions(pdlDataSource)
    val validFnrs = fnrOptions.map { it.fnr }.toSet()
    val pdlNamesByFnr = fnrOptions.associate { option ->
        option.fnr to option.label.substringAfter(" - ").takeIf { it != option.fnr }.orEmpty()
    }

    route(VEILARBVEDTAKSSTOTTE_PATH_PREFIX) {
        get {
            call.respondVeilarbvedtaksstotteOverview(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
                pdlNamesByFnr = pdlNamesByFnr,
            )
        }

        get("person/new") {
            call.respondHtml {
                veilarbvedtaksstottePersonFormPage(
                    defaults = VeilarbvedtaksstottePersonFormDefaults(
                        fnr = fnrOptions.firstOrNull()?.fnr.orEmpty(),
                        innsatsgruppe = null,
                    ),
                    actionPath = VEILARBVEDTAKSSTOTTE_PERSON_CREATE_PATH,
                    backPath = VEILARBVEDTAKSSTOTTE_PATH_PREFIX,
                    fnrOptions = fnrOptions,
                )
            }
        }

        get("person/{fnr}/edit") {
            val fnr = call.pathFnrOrRedirect(validFnrs) ?: return@get
            val existing = fetchVeilarbvedtaksstottePersonByFnr(fnr)
            if (existing == null) {
                call.redirectToVeilarbvedtaksstotte("Could not find person with fnr $fnr", isError = true)
                return@get
            }

            call.respondHtml {
                veilarbvedtaksstottePersonEditFormPage(
                    defaults = existing.toFormDefaults(),
                    actionPath = personEditPath(fnr),
                    backPath = VEILARBVEDTAKSSTOTTE_PATH_PREFIX,
                    personName = pdlNamesByFnr[fnr].orEmpty(),
                )
            }
        }

        post("person") {
            try {
                val form = call.receiveParameters().toVeilarbvedtaksstotteFormInput()
                if (!validFnrs.contains(form.fnr)) {
                    call.redirectToVeilarbvedtaksstotte("Fnr ${form.fnr} does not exist in PDL fake", isError = true)
                    return@post
                }
                if (fetchVeilarbvedtaksstottePersonByFnr(form.fnr) != null) {
                    call.redirectToVeilarbvedtaksstotte("Person with fnr ${form.fnr} already exists", isError = true)
                    return@post
                }

                insertVeilarbvedtaksstottePerson(form)
                call.redirectToVeilarbvedtaksstotte("Created person ${form.fnr}")
            } catch (exception: Exception) {
                call.redirectToVeilarbvedtaksstotte(
                    message = "Could not create person: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("person/{fnr}/edit") {
            val fnr = call.pathFnrOrRedirect(validFnrs) ?: return@post
            if (fetchVeilarbvedtaksstottePersonByFnr(fnr) == null) {
                call.redirectToVeilarbvedtaksstotte("Could not find person with fnr $fnr", isError = true)
                return@post
            }

            try {
                val form = call.receiveParameters().toVeilarbvedtaksstotteFormInput(fnrOverride = fnr)
                val updated = updateVeilarbvedtaksstottePerson(form)
                if (!updated) {
                    call.redirectToVeilarbvedtaksstotte("Could not update person with fnr $fnr", isError = true)
                    return@post
                }
                call.redirectToVeilarbvedtaksstotte("Updated person ${form.fnr}")
            } catch (exception: Exception) {
                call.redirectToVeilarbvedtaksstotte(
                    message = "Could not edit person: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("person/{fnr}/delete") {
            val fnr = call.pathFnrOrRedirect(validFnrs) ?: return@post
            val deleted = deleteVeilarbvedtaksstottePerson(fnr)
            if (!deleted) {
                call.redirectToVeilarbvedtaksstotte("Could not delete person with fnr $fnr", isError = true)
                return@post
            }
            call.redirectToVeilarbvedtaksstotte("Deleted person $fnr")
        }

        post("api/hent-gjeldende-14a-vedtak") {
            val fnr = readFnrFromBody(readRequestBody(call))
            val person = fetchVeilarbvedtaksstottePersonByFnr(fnr)

            if (person == null || person.innsatsgruppe == null) {
                respondEmpty(call, HttpStatusCode.NoContent)
            } else {
                respondJson(
                    call,
                    HttpStatusCode.OK,
                    veilarbvedtaksstotteObjectMapper.writeValueAsString(
                        Gjeldende14aVedtakResponse(innsatsgruppe = person.innsatsgruppe.name),
                    ),
                )
            }
        }
    }
}

private fun readFnrFromBody(body: String): String {
    val node = veilarbvedtaksstotteObjectMapper.readTree(body)
    return node.path("fnr").asText("")
}

private suspend fun ApplicationCall.respondVeilarbvedtaksstotteOverview(
    message: String?,
    isError: Boolean,
    pdlNamesByFnr: Map<String, String>,
) {
    val persons = fetchVeilarbvedtaksstottePersons()
    respondHtml {
        veilarbvedtaksstottePage(
            persons = persons,
            message = message,
            isError = isError,
            newPersonPath = VEILARBVEDTAKSSTOTTE_PERSON_NEW_PATH,
            editPersonPathPrefix = "$VEILARBVEDTAKSSTOTTE_PATH_PREFIX/person",
            pdlNamesByFnr = pdlNamesByFnr,
        )
    }
}

private fun Parameters.toVeilarbvedtaksstotteFormInput(fnrOverride: String? = null): VeilarbvedtaksstottePersonFormInput {
    val fnr = fnrOverride ?: (this["fnr"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Missing required form field 'fnr'"))
    val innsatsgruppeRaw = this["innsatsgruppe"]?.trim().orEmpty()
    val innsatsgruppe = if (innsatsgruppeRaw.isBlank()) null else enumValueOf<InnsatsgruppeV2>(innsatsgruppeRaw)

    return VeilarbvedtaksstottePersonFormInput(
        fnr = fnr,
        innsatsgruppe = innsatsgruppe,
    )
}

private suspend fun ApplicationCall.pathFnrOrRedirect(validFnrs: Set<String>): String? {
    val fnr = parameters["fnr"]?.trim()
    if (fnr.isNullOrBlank()) {
        redirectToVeilarbvedtaksstotte("Missing path parameter 'fnr'", isError = true)
        return null
    }
    if (!fnr.matches(Regex("\\d{11}"))) {
        redirectToVeilarbvedtaksstotte("Invalid fnr '$fnr'", isError = true)
        return null
    }
    if (!validFnrs.contains(fnr)) {
        redirectToVeilarbvedtaksstotte("Fnr '$fnr' does not exist in PDL fake", isError = true)
        return null
    }
    return fnr
}

private suspend fun ApplicationCall.redirectToVeilarbvedtaksstotte(message: String, isError: Boolean = false) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$VEILARBVEDTAKSSTOTTE_PATH_PREFIX?message=$encodedMessage&isError=$isError")
}

private fun personEditPath(fnr: String): String = "$VEILARBVEDTAKSSTOTTE_PATH_PREFIX/person/$fnr/edit"

private data class Gjeldende14aVedtakResponse(
    val innsatsgruppe: String,
)


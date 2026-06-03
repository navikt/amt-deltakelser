import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import pdl.PdlDataSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val DOKDISTKANAL_PATH_PREFIX = "/dokdistkanal"

private const val DOKDISTKANAL_PERSON_NEW_PATH = "$DOKDISTKANAL_PATH_PREFIX/person/new"
private const val DOKDISTKANAL_PERSON_CREATE_PATH = "$DOKDISTKANAL_PATH_PREFIX/person"

private val dokdistkanalObjectMapper = jacksonObjectMapper()

fun Route.dokdistkanalFakeRoutes(pdlDataSource: PdlDataSource) {
    val personidentOptions = buildDokdistkanalPersonidentOptions(pdlDataSource)
    val validPersonidenter = personidentOptions.map { it.personident }.toSet()
    val pdlNamesByPersonident = personidentOptions.associate { option ->
        option.personident to option.label.substringAfter(" - ").takeIf { it != option.personident }.orEmpty()
    }

    route(DOKDISTKANAL_PATH_PREFIX) {
        get {
            call.respondDokdistkanalOverview(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
                pdlNamesByPersonident = pdlNamesByPersonident,
            )
        }

        get("person/new") {
            call.respondHtml {
                dokdistkanalPersonFormPage(
                    defaults = DokdistkanalPersonFormDefaults(
                        personident = personidentOptions.firstOrNull()?.personident.orEmpty(),
                        distribusjonskanal = DokdistkanalDistribusjonskanal.DITT_NAV,
                    ),
                    actionPath = DOKDISTKANAL_PERSON_CREATE_PATH,
                    backPath = DOKDISTKANAL_PATH_PREFIX,
                    personidentOptions = personidentOptions,
                )
            }
        }

        get("person/{personident}/edit") {
            val personident = call.pathPersonidentOrRedirect(validPersonidenter) ?: return@get
            val existing = fetchDokdistkanalPersonByPersonident(personident)
            if (existing == null) {
                call.redirectToDokdistkanal("Could not find person with personident $personident", isError = true)
                return@get
            }

            call.respondHtml {
                dokdistkanalPersonEditFormPage(
                    defaults = existing.toFormDefaults(),
                    actionPath = personEditPath(personident),
                    backPath = DOKDISTKANAL_PATH_PREFIX,
                    personName = pdlNamesByPersonident[personident].orEmpty(),
                )
            }
        }

        post("person") {
            try {
                val form = call.receiveParameters().toDokdistkanalFormInput()
                if (!validPersonidenter.contains(form.personident)) {
                    call.redirectToDokdistkanal("Personident ${form.personident} does not exist in PDL fake", isError = true)
                    return@post
                }
                if (fetchDokdistkanalPersonByPersonident(form.personident) != null) {
                    call.redirectToDokdistkanal("Person with personident ${form.personident} already exists", isError = true)
                    return@post
                }

                insertDokdistkanalPerson(form)
                call.redirectToDokdistkanal("Created person ${form.personident}")
            } catch (exception: Exception) {
                call.redirectToDokdistkanal(
                    message = "Could not create person: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("person/{personident}/edit") {
            val personident = call.pathPersonidentOrRedirect(validPersonidenter) ?: return@post
            if (fetchDokdistkanalPersonByPersonident(personident) == null) {
                call.redirectToDokdistkanal("Could not find person with personident $personident", isError = true)
                return@post
            }

            try {
                val form = call.receiveParameters().toDokdistkanalFormInput(personidentOverride = personident)
                val updated = updateDokdistkanalPerson(form)
                if (!updated) {
                    call.redirectToDokdistkanal("Could not update person with personident $personident", isError = true)
                    return@post
                }
                call.redirectToDokdistkanal("Updated person ${form.personident}")
            } catch (exception: Exception) {
                call.redirectToDokdistkanal(
                    message = "Could not edit person: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("person/{personident}/delete") {
            val personident = call.pathPersonidentOrRedirect(validPersonidenter) ?: return@post
            val deleted = deleteDokdistkanalPerson(personident)
            if (!deleted) {
                call.redirectToDokdistkanal("Could not delete person with personident $personident", isError = true)
                return@post
            }
            call.redirectToDokdistkanal("Deleted person $personident")
        }

        post("rest/bestemDistribusjonskanal") {
            val request = readBestemDistribusjonskanalRequest(readRequestBody(call))
            val distribusjonskanal = fetchDokdistkanalPersonByPersonident(request.brukerId)
                ?.distribusjonskanal
                ?: DokdistkanalDistribusjonskanal.PRINT

            respondJson(
                call,
                HttpStatusCode.OK,
                dokdistkanalObjectMapper.writeValueAsString(
                    BestemDistribusjonskanalResponse(distribusjonskanal = distribusjonskanal.name),
                ),
            )
        }
    }
}

private fun readBestemDistribusjonskanalRequest(body: String): BestemDistribusjonskanalRequest {
    val node = dokdistkanalObjectMapper.readTree(body)
    return BestemDistribusjonskanalRequest(
        brukerId = node.path("brukerId").asText(""),
    )
}

private suspend fun ApplicationCall.respondDokdistkanalOverview(
    message: String?,
    isError: Boolean,
    pdlNamesByPersonident: Map<String, String>,
) {
    val persons = fetchDokdistkanalPersons()
    respondHtml {
        dokdistkanalPage(
            persons = persons,
            message = message,
            isError = isError,
            newPersonPath = DOKDISTKANAL_PERSON_NEW_PATH,
            editPersonPathPrefix = "$DOKDISTKANAL_PATH_PREFIX/person",
            pdlNamesByPersonident = pdlNamesByPersonident,
        )
    }
}

private fun Parameters.toDokdistkanalFormInput(personidentOverride: String? = null): DokdistkanalPersonFormInput {
    val personident = personidentOverride ?: (this["personident"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Missing required form field 'personident'"))
    val distribusjonskanal = enumValueOf<DokdistkanalDistribusjonskanal>(
        this["distribusjonskanal"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("Missing required form field 'distribusjonskanal'"),
    )

    return DokdistkanalPersonFormInput(
        personident = personident,
        distribusjonskanal = distribusjonskanal,
    )
}

private suspend fun ApplicationCall.pathPersonidentOrRedirect(validPersonidenter: Set<String>): String? {
    val personident = parameters["personident"]?.trim()
    if (personident.isNullOrBlank()) {
        redirectToDokdistkanal("Missing path parameter 'personident'", isError = true)
        return null
    }
    if (!personident.matches(Regex("\\d{11}"))) {
        redirectToDokdistkanal("Invalid personident '$personident'", isError = true)
        return null
    }
    if (!validPersonidenter.contains(personident)) {
        redirectToDokdistkanal("Personident '$personident' does not exist in PDL fake", isError = true)
        return null
    }
    return personident
}

private suspend fun ApplicationCall.redirectToDokdistkanal(message: String, isError: Boolean = false) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$DOKDISTKANAL_PATH_PREFIX?message=$encodedMessage&isError=$isError")
}

private fun personEditPath(personident: String): String = "$DOKDISTKANAL_PATH_PREFIX/person/$personident/edit"

private data class BestemDistribusjonskanalRequest(
    val brukerId: String,
)

private data class BestemDistribusjonskanalResponse(
    val distribusjonskanal: String,
)


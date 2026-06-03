package tjenester.nav.nom

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import http.respondGraphqlFake
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import tjenester.nav.pdl.PdlDataSource
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val NOM_PATH_PREFIX = "/nom"

private const val NOM_RESSURS_NEW_PATH = "$NOM_PATH_PREFIX/ressurs/new"
private const val NOM_RESSURS_CREATE_PATH = "$NOM_PATH_PREFIX/ressurs"

private val nomObjectMapper = jacksonObjectMapper().findAndRegisterModules()
private val nomGraphql = createNomGraphql(
    ressurserDataFetcher = { environment ->
        val where = environment.getArgument<Map<String, Any?>?>("where") ?: emptyMap()
        val navidenter = readNavidenter(where)

        toRessurserResult(navidenter)
    },
)

fun Route.nomFakeRoutes(pdlDataSource: PdlDataSource) {
    val pdlPersons = pdlDataSource.allPersons()
    val pdlNamesByPersonident = pdlPersons
        .mapValues { (_, person) ->
            person.navn.firstOrNull()?.let {
                listOfNotNull(it.fornavn, it.mellomnavn, it.etternavn).joinToString(" ")
            }.orEmpty()
        }
    val personidentOptions = pdlPersons.keys
        .filter { it.matches(Regex("\\d{11}")) }
        .distinct()
        .sorted()
        .map { personident ->
            val name = pdlNamesByPersonident[personident].orEmpty()
            NomPersonidentOption(
                personident = personident,
                label = if (name.isBlank()) personident else "$personident - $name",
            )
        }
    val validPersonidenter = personidentOptions.map { it.personident }.toSet()
    val pdlFornavn = pdlPersons.mapValues { (_, p) -> p.navn.firstOrNull()?.fornavn.orEmpty() }
    val pdlEtternavn = pdlPersons.mapValues { (_, p) -> p.navn.firstOrNull()?.etternavn.orEmpty() }

    route(NOM_PATH_PREFIX) {
        get {
            call.respondNomOverview(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
                pdlNamesByPersonident = pdlNamesByPersonident,
            )
        }

        get("ressurs/new") {
            val suggestedNavident = nextNavident()
            val firstPersonident = personidentOptions.firstOrNull()?.personident.orEmpty()

            call.respondHtml {
                nomRessursFormPage(
                    defaults = defaultNomRessursFormDefaults().copy(
                        navident = suggestedNavident,
                        personident = firstPersonident,
                    ),
                    actionPath = NOM_RESSURS_CREATE_PATH,
                    backPath = NOM_PATH_PREFIX,
                    personidentOptions = personidentOptions,
                )
            }
        }

        get("ressurs/{navident}/edit") {
            val navident = call.pathNavidentOrRedirect() ?: return@get
            val existing = fetchNomRessursByNavident(navident)
            if (existing == null) {
                call.redirectToNom("Could not find ressurs $navident", isError = true)
                return@get
            }

            call.respondHtml {
                nomRessursEditFormPage(
                    defaults = existing.toFormDefaults(),
                    actionPath = ressursEditPath(navident),
                    backPath = NOM_PATH_PREFIX,
                    personidentOptions = personidentOptions,
                )
            }
        }

        post("ressurs") {
            try {
                val form = call.receiveParameters().toNomRessursCreateInput(validPersonidenter, pdlFornavn, pdlEtternavn)
                if (fetchNomRessursByNavident(form.navident) != null) {
                    call.redirectToNom("Ressurs ${form.navident} already exists", isError = true)
                    return@post
                }

                insertNomRessurs(form)
                call.redirectToNom("Created ressurs ${form.navident}")
            } catch (exception: Exception) {
                call.redirectToNom(
                    message = "Could not create ressurs: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("ressurs/{navident}/edit") {
            val navident = call.pathNavidentOrRedirect() ?: return@post
            if (fetchNomRessursByNavident(navident) == null) {
                call.redirectToNom("Could not find ressurs $navident", isError = true)
                return@post
            }

            try {
                val form = call.receiveParameters().toNomRessursEditInput(validPersonidenter, navident)
                val updated = updateNomRessurs(form)
                if (!updated) {
                    call.redirectToNom("Could not update ressurs $navident", isError = true)
                    return@post
                }

                call.redirectToNom("Updated ressurs ${form.navident}")
            } catch (exception: Exception) {
                call.redirectToNom(
                    message = "Could not edit ressurs: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("ressurs/{navident}/delete") {
            val navident = call.pathNavidentOrRedirect() ?: return@post
            if (isNomRessursUsedByVeilarboppfolging(navident)) {
                call.redirectToNom("Could not delete $navident because it is in use by veilarboppfolging", isError = true)
                return@post
            }

            val deleted = deleteNomRessurs(navident)
            if (!deleted) {
                call.redirectToNom("Could not delete ressurs $navident", isError = true)
                return@post
            }

            call.redirectToNom("Deleted ressurs $navident")
        }

        post("graphql") {
            respondGraphqlFake(call, nomObjectMapper, nomGraphql)
        }
    }
}

private fun readNavidenter(where: Map<String, Any?>): List<String> =
    readStringList(where["navidenter"]) ?: readStringList(where["navIdenter"]) ?: emptyList()

private fun readStringList(value: Any?): List<String>? {
    val entries = value as? List<*> ?: return null
    return entries.mapNotNull { it?.toString() }
}

private fun toRessurserResult(navidenter: List<String>): List<Any?> {
    val resourcesByNavident = fetchNomRessurser().associateBy { it.navident }

    return navidenter.map { navident ->
        val ressurs = resourcesByNavident[navident]?.toRessursFixture()

        if (ressurs != null && ressurs.navident == navident) {
            mapOf(
                "code" to "OK",
                "id" to navident,
                "ressurs" to ressurs,
            )
        } else {
            mapOf(
                "code" to "NOT_FOUND",
                "id" to navident,
                "ressurs" to null,
            )
        }
    }
}

private suspend fun ApplicationCall.respondNomOverview(
    message: String?,
    isError: Boolean,
    pdlNamesByPersonident: Map<String, String>,
) {
    val ressurser = fetchNomRessurser()

    respondHtml {
        nomPage(
            ressurser = ressurser,
            message = message,
            isError = isError,
            newRessursPath = NOM_RESSURS_NEW_PATH,
            editRessursPathPrefix = "$NOM_PATH_PREFIX/ressurs",
            pdlNamesByPersonident = pdlNamesByPersonident,
        )
    }
}

private fun Parameters.toNomRessursCreateInput(
    validPersonidenter: Set<String>,
    pdlFornavn: Map<String, String>,
    pdlEtternavn: Map<String, String>,
): NomRessursFormInput {
    val navident = required("navident").uppercase()
    require(navident.matches(Regex("[A-Z]\\d{6}"))) { "Field 'navident' must match pattern [A-Z]\\d{6}" }
    val personident = required("personident")
    require(validPersonidenter.contains(personident)) { "Field 'personident' must reference an existing PDL person" }

    val fornavn = pdlFornavn[personident].orEmpty()
    val etternavn = pdlEtternavn[personident].orEmpty()
    val visningsnavn = listOfNotNull(fornavn.takeIf { it.isNotBlank() }, etternavn.takeIf { it.isNotBlank() })
        .joinToString(" ")
    val epost = if (fornavn.isNotBlank() && etternavn.isNotBlank()) {
        "${fornavn.lowercase()}.${etternavn.lowercase()}@nav.sim.no"
    } else {
        ""
    }

    return NomRessursFormInput(
        navident = navident,
        personident = personident,
        visningsnavn = visningsnavn,
        fornavn = fornavn,
        etternavn = etternavn,
        epost = epost,
        primaryTelefon = null,
        telefon = emptyList(),
        orgTilknytning = emptyList(),
    )
}

private fun Parameters.toNomRessursEditInput(
    validPersonidenter: Set<String>,
    navidentOverride: String,
): NomRessursFormInput {
    val personident = required("personident")
    require(validPersonidenter.contains(personident)) { "Field 'personident' must reference an existing PDL person" }

    return NomRessursFormInput(
        navident = navidentOverride,
        personident = personident,
        visningsnavn = required("visningsnavn"),
        fornavn = required("fornavn"),
        etternavn = required("etternavn"),
        epost = required("epost"),
        primaryTelefon = this["primaryTelefon"]?.trim()?.takeIf { it.isNotBlank() },
        telefon = parseNomTelefonJson(required("telefon")),
        orgTilknytning = parseNomOrgTilknytningJson(required("orgTilknytning")),
    )
}

private fun Parameters.required(name: String): String {
    return this[name]?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Missing required form field '$name'")
}

private suspend fun ApplicationCall.pathNavidentOrRedirect(): String? {
    val navident = parameters["navident"]?.uppercase()
    if (navident.isNullOrBlank()) {
        redirectToNom("Missing path parameter 'navident'", isError = true)
        return null
    }

    if (!navident.matches(Regex("[A-Z]\\d{6}"))) {
        redirectToNom("Invalid navident '$navident'", isError = true)
        return null
    }

    return navident
}

private suspend fun ApplicationCall.redirectToNom(message: String, isError: Boolean = false) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$NOM_PATH_PREFIX?message=$encodedMessage&isError=$isError")
}

private fun ressursEditPath(navident: String): String = "$NOM_PATH_PREFIX/ressurs/$navident/edit"




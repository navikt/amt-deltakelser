package tjenester.nav.aooppfolgingskontor

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

const val AO_OPPFOLGINGSKONTOR_PATH_PREFIX = "/ao-oppfolgingskontor"

private const val AO_OPPFOLGINGSKONTOR_NEW_PATH = "$AO_OPPFOLGINGSKONTOR_PATH_PREFIX/kontor-tilhorighet/new"
private const val AO_OPPFOLGINGSKONTOR_CREATE_PATH = "$AO_OPPFOLGINGSKONTOR_PATH_PREFIX/kontor-tilhorighet"

private val aoOppfolgingskontorObjectMapper = jacksonObjectMapper().findAndRegisterModules()
private val aoOppfolgingskontorGraphql = createAoOppfolgingskontorGraphql(
    kontorTilhorigheterDataFetcher = { environment ->
        val ident = environment.getArgument<String>("ident") ?: ""
        fetchKontorTilhorigheterByIdent(ident)
            ?: KontorTilhorigheterFixture()
    },
)

fun Route.aoOppfolgingskontorFakeRoutes(
    pdlDataSource: PdlDataSource,
    norgOptions: List<AoOppfolgingskontorNorgKontorOption>,
) {
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
            AoOppfolgingskontorPersonidentOption(
                personident = personident,
                label = if (name.isBlank()) personident else "$personident - $name",
            )
        }
    val validPersonidenter = personidentOptions.map { it.personident }.toSet()

    val norgKontorById = norgOptions.associateBy { it.kontorId }

    route(AO_OPPFOLGINGSKONTOR_PATH_PREFIX) {
        get {
            call.respondAoOppfolgingskontorOverview(
                message = call.request.queryParameters["message"],
                isError = call.request.queryParameters["isError"].toBoolean(),
                pdlNamesByPersonident = pdlNamesByPersonident,
            )
        }

        get("kontor-tilhorighet/new") {
            call.respondHtml {
                aoOppfolgingskontorFormPage(
                    defaults = defaultAoOppfolgingskontorFormDefaults(personidentOptions),
                    actionPath = AO_OPPFOLGINGSKONTOR_CREATE_PATH,
                    backPath = AO_OPPFOLGINGSKONTOR_PATH_PREFIX,
                    personidentOptions = personidentOptions,
                    norgKontorOptions = norgOptions,
                )
            }
        }

        get("kontor-tilhorighet/{ident}/edit") {
            val ident = call.pathIdentOrRedirect() ?: return@get
            val existing = fetchAoOppfolgingskontorByIdent(ident)
            if (existing == null) {
                call.redirectToAoOppfolgingskontor("Could not find kontor-tilhorighet for ident $ident", isError = true)
                return@get
            }

            call.respondHtml {
                aoOppfolgingskontorEditFormPage(
                    defaults = existing.toFormDefaults(),
                    actionPath = kontorTilhorighetEditPath(ident),
                    backPath = AO_OPPFOLGINGSKONTOR_PATH_PREFIX,
                    norgKontorOptions = norgOptions,
                    personName = pdlNamesByPersonident[ident].orEmpty(),
                )
            }
        }

        post("kontor-tilhorighet") {
            try {
                val form = call.receiveParameters().toAoOppfolgingskontorCreateInput(validPersonidenter, norgKontorById)
                if (fetchAoOppfolgingskontorByIdent(form.ident) != null) {
                    call.redirectToAoOppfolgingskontor("Kontor-tilhorighet for ident ${form.ident} already exists", isError = true)
                    return@post
                }

                insertAoOppfolgingskontorKontorTilhorighet(form)
                call.redirectToAoOppfolgingskontor("Created kontor-tilhorighet for ident ${form.ident}")
            } catch (exception: Exception) {
                call.redirectToAoOppfolgingskontor(
                    message = "Could not create kontor-tilhorighet: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("kontor-tilhorighet/{ident}/edit") {
            val ident = call.pathIdentOrRedirect() ?: return@post
            if (fetchAoOppfolgingskontorByIdent(ident) == null) {
                call.redirectToAoOppfolgingskontor("Could not find kontor-tilhorighet for ident $ident", isError = true)
                return@post
            }

            try {
                val form = call.receiveParameters().toAoOppfolgingskontorEditInput(ident, norgKontorById)
                val updated = updateAoOppfolgingskontorKontorTilhorighet(form)
                if (!updated) {
                    call.redirectToAoOppfolgingskontor("Could not update kontor-tilhorighet for ident $ident", isError = true)
                    return@post
                }

                call.redirectToAoOppfolgingskontor("Updated kontor-tilhorighet for ident ${form.ident}")
            } catch (exception: Exception) {
                call.redirectToAoOppfolgingskontor(
                    message = "Could not edit kontor-tilhorighet: ${exception.message ?: "unknown error"}",
                    isError = true,
                )
            }
        }

        post("kontor-tilhorighet/{ident}/delete") {
            val ident = call.pathIdentOrRedirect() ?: return@post
            val deleted = deleteAoOppfolgingskontorKontorTilhorighet(ident)
            if (!deleted) {
                call.redirectToAoOppfolgingskontor("Could not delete kontor-tilhorighet for ident $ident", isError = true)
                return@post
            }

            call.redirectToAoOppfolgingskontor("Deleted kontor-tilhorighet for ident $ident")
        }

        post("graphql") {
            respondGraphqlFake(call, aoOppfolgingskontorObjectMapper, aoOppfolgingskontorGraphql)
        }
    }
}

private suspend fun ApplicationCall.respondAoOppfolgingskontorOverview(
    message: String?,
    isError: Boolean,
    pdlNamesByPersonident: Map<String, String>,
) {
    val rows = fetchAoOppfolgingskontorKontorTilhorigheter()

    respondHtml {
        aoOppfolgingskontorPage(
            rows = rows,
            message = message,
            isError = isError,
            newPath = AO_OPPFOLGINGSKONTOR_NEW_PATH,
            editPathPrefix = "$AO_OPPFOLGINGSKONTOR_PATH_PREFIX/kontor-tilhorighet",
            pdlNamesByPersonident = pdlNamesByPersonident,
        )
    }
}

private fun Parameters.toAoOppfolgingskontorCreateInput(
    validPersonidenter: Set<String>,
    norgKontorById: Map<String, AoOppfolgingskontorNorgKontorOption>,
): AoOppfolgingskontorFormInput {
    return AoOppfolgingskontorFormInput(
        ident = required("ident"),
        arbeidsoppfolging = parseArbeidsoppfolging(norgKontorById),
    ).validate(validPersonidenter)
}

private fun Parameters.toAoOppfolgingskontorEditInput(
    identOverride: String,
    norgKontorById: Map<String, AoOppfolgingskontorNorgKontorOption>,
): AoOppfolgingskontorFormInput {
    return AoOppfolgingskontorFormInput(
        ident = identOverride,
        arbeidsoppfolging = parseArbeidsoppfolging(norgKontorById),
    )
}

private fun Parameters.parseArbeidsoppfolging(
    norgKontorById: Map<String, AoOppfolgingskontorNorgKontorOption>,
): ArbeidsoppfolgingFixture? {
    val kontorId = this["arbeidsoppfolgingKontorId"]?.trim().orEmpty()
    if (kontorId.isBlank()) {
        return null
    }

    val norgKontor = norgKontorById[kontorId]
        ?: error("Field 'arbeidsoppfolgingKontorId' must reference an existing Norg enhet")

    return ArbeidsoppfolgingFixture(
        kontorId = norgKontor.kontorId,
        kontorNavn = norgKontor.kontorNavn,
    )
}

private fun AoOppfolgingskontorFormInput.validate(validPersonidenter: Set<String>): AoOppfolgingskontorFormInput {
    require(validPersonidenter.contains(ident)) { "Field 'ident' must reference an existing PDL person" }
    return this
}

private fun Parameters.required(name: String): String {
    return this[name]?.trim()?.takeIf { it.isNotEmpty() }
        ?: error("Missing required form field '$name'")
}

private suspend fun ApplicationCall.pathIdentOrRedirect(): String? {
    val ident = parameters["ident"]?.trim()
    if (ident.isNullOrBlank()) {
        redirectToAoOppfolgingskontor("Missing path parameter 'ident'", isError = true)
        return null
    }

    if (!ident.matches(Regex("\\d{11}"))) {
        redirectToAoOppfolgingskontor("Invalid ident '$ident'", isError = true)
        return null
    }

    return ident
}

private suspend fun ApplicationCall.redirectToAoOppfolgingskontor(message: String, isError: Boolean = false) {
    val encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8)
    respondRedirect("$AO_OPPFOLGINGSKONTOR_PATH_PREFIX?message=$encodedMessage&isError=$isError")
}

private fun kontorTilhorighetEditPath(ident: String): String =
    "$AO_OPPFOLGINGSKONTOR_PATH_PREFIX/kontor-tilhorighet/$ident/edit"



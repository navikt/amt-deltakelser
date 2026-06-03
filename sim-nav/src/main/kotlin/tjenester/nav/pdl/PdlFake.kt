package tjenester.nav.pdl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import http.respondGraphqlFake
import io.ktor.server.routing.*

const val PDL_PATH_PREFIX = "/pdl"

private val pdlObjectMapper = jacksonObjectMapper()

fun Route.pdlFakeRoutes(dataSource: PdlDataSource) {
    val pdlGraphql = createPdlGraphql(
        hentPersonDataFetcher = { environment ->
            val ident = environment.getArgument<String>("ident") ?: ""
            dataSource.findPerson(ident)
        },
        hentIdenterDataFetcher = { environment ->
            val ident = environment.getArgument<String>("ident") ?: ""
            val grupper = environment.getArgument<List<Any?>?>("grupper")
                ?.mapNotNull { it?.toString() }
            val historikk = environment.getArgument<Boolean?>("historikk")

            mapOf(
                "identer" to dataSource
                    .findPerson(ident)
                    .filteredIdenter(grupper = grupper, historikk = historikk),
            )
        },
    )

    route(PDL_PATH_PREFIX) {

        post("graphql") {
            respondGraphqlFake(call, pdlObjectMapper, pdlGraphql)
        }
    }
}

private fun PdlPersonFixture.filteredIdenter(
    grupper: List<String>?,
    historikk: Boolean?,
): List<IdentInformasjonFixture> {
    val includeHistorical = historikk == true

    return identer.filter { identInfo ->
        val isRequestedGroup = grupper.isNullOrEmpty() || grupper.contains(identInfo.gruppe)
        val isRequestedHistoricalState = includeHistorical || !identInfo.historisk

        isRequestedGroup && isRequestedHistoricalState
    }
}


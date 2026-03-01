package no.nav.amt.deltaker.external.api

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import no.nav.amt.deltaker.application.plugins.getNavAnsattAzureId
import no.nav.amt.deltaker.auth.TilgangskontrollService
import no.nav.amt.deltaker.deltaker.db.DeltakerRepository
import no.nav.amt.deltaker.deltaker.model.Deltaker
import no.nav.amt.deltaker.external.DeltakelserResponseMapper
import no.nav.amt.deltaker.external.data.DeltakerPersonaliaResponse
import no.nav.amt.deltaker.external.data.HarAktiveDeltakelserResponse
import no.nav.amt.deltaker.external.data.HentDeltakelserRequest
import no.nav.amt.deltaker.navenhet.NavEnhetService
import no.nav.amt.lib.utils.unleash.CommonUnleashToggle
import java.util.UUID

fun Routing.registerExternalApi(
    deltakerRepository: DeltakerRepository,
    navEnhetService: NavEnhetService,
    tilgangskontrollService: TilgangskontrollService,
    deltakelserResponseMapper: DeltakelserResponseMapper,
    unleashToggle: CommonUnleashToggle,
) {
    val apiPath = "/external"

    authenticate("EXTERNAL-SYSTEM") {
        post("$apiPath/aktiv-deltaker") {
            // Brukes av veilarboppfolging til å bestemme om oppfølgingsperioden kan avsluttes
            val request = call.receive<HentDeltakelserRequest>()
            val deltakelser = deltakerRepository.getFlereForPerson(request.norskIdent)
            val harAktiveDeltakelser = deltakelser.any { deltaker -> deltaker.status.erAktiv() }
            call.respond(HarAktiveDeltakelserResponse(harAktiveDeltakelser))
        }
    }

    authenticate("MULIGHETSROMMET-SYSTEM") {
        // Brukes av mulighetsrommet for å hente personalia på deltakere i deres økonomi-løsning
        // Dokumentasjon: docs/external-deltakere-personalia.md
        post("$apiPath/deltakere/personalia") {
            val deltakerIdListe = call.receive<List<DeltakerID>>()
            val deltakere: List<Deltaker> = deltakerRepository.getMany(deltakerIdListe.toSet())
            val navEnheter = navEnhetService.getEnheter(deltakere.mapNotNull { it.navBruker.navEnhetId }.toSet())

            call.respond(deltakere.map { DeltakerPersonaliaResponse.from(it, navEnheter) })
        }
    }

    authenticate("VEILEDER") {
        post("/deltakelser") {
            // API til valp hvor de henter ut alle deltakelser til person
            val request = call.receive<HentDeltakelserRequest>()
            tilgangskontrollService.verifiserLesetilgang(call.getNavAnsattAzureId(), request.norskIdent)

            val deltakelser = deltakerRepository
                .getFlereForPerson(request.norskIdent)
                .filter {
                    unleashToggle.erKometMasterForTiltakstype(it.deltakerliste.tiltakstype.tiltakskode) ||
                        unleashToggle.skalLeseArenaDataForTiltakstype(it.deltakerliste.tiltakstype.tiltakskode)
                }

            val responseBody = deltakelserResponseMapper.toDeltakelserResponse(deltakelser)
            call.respond(responseBody)
        }
    }
}

private typealias DeltakerID = UUID

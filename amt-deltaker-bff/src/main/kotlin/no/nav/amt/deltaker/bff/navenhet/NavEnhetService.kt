package no.nav.amt.deltaker.bff.navenhet

import no.nav.amt.lib.ktor.clients.AmtPersonServiceClient
import no.nav.amt.lib.models.deltaker.DeltakerHistorikk
import no.nav.amt.lib.models.person.NavEnhet
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

class NavEnhetService(
    private val repository: NavEnhetRepository,
    private val amtPersonServiceClient: AmtPersonServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun hentOpprettEllerOppdaterNavEnhet(enhetsnummer: String): NavEnhet {
        repository
            .get(enhetsnummer)
            ?.takeIf { it.sistEndret.isAfter(LocalDateTime.now().minusMonths(1)) }
            ?.let { return it.toNavEnhet() }

        log.info("Fant ikke oppdatert nav-enhet med nummer $enhetsnummer, henter fra amt-person-service")
        val navEnhet = amtPersonServiceClient.hentNavEnhet(enhetsnummer)
        return upsert(navEnhet)
    }

    suspend fun hentEllerOpprettEnhet(id: UUID): NavEnhet {
        repository.get(id)?.let { return it.toNavEnhet() }

        val navEnhet = amtPersonServiceClient.hentNavEnhet(id)
        return upsert(navEnhet)
    }

    fun hentEnhet(id: UUID) = repository.get(id)?.toNavEnhet()

    suspend fun hentEnheterForHistorikk(historikk: List<DeltakerHistorikk>): Map<UUID, NavEnhet> {
        val ider = historikk.flatMap { it.navEnheter() }.distinct()
        val enheterFraDb = hentEnheter(ider)

        return if (ider.size != enheterFraDb.size) {
            enheterFraDb + hentManglendeEnheter(ider, enheterFraDb).associateBy { it.id }
        } else {
            enheterFraDb
        }
    }

    private suspend fun hentManglendeEnheter(ider: List<UUID>, lagredeEnheter: Map<UUID, NavEnhet>): List<NavEnhet> {
        val manglendeEnheter = ider.toSet() - lagredeEnheter.keys
        return manglendeEnheter.map { hentEllerOpprettEnhet(it) }
    }

    fun hentEnheter(enhetIder: List<UUID>) = repository.getMany(enhetIder).map { it.toNavEnhet() }.associateBy { it.id }

    fun upsert(enhet: NavEnhet) = repository.upsert(enhet).toNavEnhet()
}

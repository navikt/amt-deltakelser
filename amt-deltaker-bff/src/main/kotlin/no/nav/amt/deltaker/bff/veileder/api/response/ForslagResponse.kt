package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.ForslagDecorator
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import java.time.LocalDateTime
import java.util.UUID

data class ForslagResponse(
    val id: UUID,
    val opprettet: LocalDateTime,
    val begrunnelse: String?,
    val arrangorNavn: String,
    val endring: Forslag.Endring,
    val status: ForslagResponseStatus,
) : DeltakerHistorikkResponse {
    companion object {
        /** Extension som mapper Forslag.Status til ForslagResponseStatus */
        private fun Forslag.Status.toResponseStatus(
            decorator: ForslagDecorator,
            avvistAvNavnProvider: (UUID) -> String,
            avvistAvEnhetNavnProvider: (UUID) -> String,
        ): ForslagResponseStatus = when (this) {
            is Forslag.Status.VenterPaSvar -> ForslagResponseStatus.VenterPaSvar
            is Forslag.Status.Godkjent -> ForslagResponseStatus.Godkjent(godkjent)
            is Forslag.Status.Tilbakekalt -> ForslagResponseStatus.Tilbakekalt(tilbakekalt)
            is Forslag.Status.Erstattet -> ForslagResponseStatus.Erstattet(erstattet)
            is Forslag.Status.Avvist -> {
                // Hent avvist-info fra decorator hvis det er AvvistStatusDecorator
                val (avvistAvNavn, avvistAvEnhet) = (decorator as? ForslagDecorator.AvvistStatusDecorator)
                    ?.let { it.avvistAvAnsattNavn to it.avvistAvEnhetNavn }
                    ?: (avvistAvNavnProvider(avvistAv.id) to avvistAvEnhetNavnProvider(avvistAv.enhetId))

                ForslagResponseStatus.Avvist(
                    avvistAv = avvistAvNavn,
                    avvistAvEnhet = avvistAvEnhet,
                    avvist = avvist,
                    begrunnelseFraNav = begrunnelseFraNav,
                )
            }
        }

        /** Lager en ForslagResponse fra et ForslagDecorator */
        fun fromForslagDecorator(
            dekorertForslag: ForslagDecorator,
            arrangornavn: String,
            avvistAvNavnProvider: (UUID) -> String = { it.toString() },
            avvistAvEnhetNavnProvider: (UUID) -> String = { it.toString() },
        ): ForslagResponse {
            val forslag = dekorertForslag.forslag
            return ForslagResponse(
                id = forslag.id,
                opprettet = forslag.opprettet,
                begrunnelse = forslag.begrunnelse,
                arrangorNavn = arrangornavn,
                endring = forslag.endring,
                status = forslag.status.toResponseStatus(
                    decorator = dekorertForslag,
                    avvistAvNavnProvider = avvistAvNavnProvider,
                    avvistAvEnhetNavnProvider = avvistAvEnhetNavnProvider,
                ),
            )
        }

        /** Lager en ForslagResponse direkte fra et Forslag */
        fun fromForslag(
            forslag: Forslag,
            arrangornavn: String,
            enheter: Map<UUID, NavEnhet>,
            ansatte: Map<UUID, NavAnsatt>,
        ): ForslagResponse = fromForslagDecorator(
            dekorertForslag = ForslagDecorator.DefaultDecorator(forslag),
            arrangornavn = arrangornavn,
            // NOTE: Fallback til UUID-string hvis ikke funnet i ansatte
            avvistAvNavnProvider = { ansatte[it]?.navn ?: it.toString() },
            // NOTE: Fallback til UUID-string hvis ikke funnet i enheter
            avvistAvEnhetNavnProvider = { enheter[it]?.navn ?: it.toString() },
        )
    }
}

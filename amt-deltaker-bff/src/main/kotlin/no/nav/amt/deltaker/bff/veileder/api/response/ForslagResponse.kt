package no.nav.amt.deltaker.bff.veileder.api.response

import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.amt.lib.models.arrangor.melding.ForslagDecorator
import no.nav.amt.lib.models.person.NavAnsatt
import no.nav.amt.lib.models.person.NavEnhet
import no.nav.amt.lib.utils.GenericCache
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
            decorator: ForslagDecorator,
            arrangorNavn: String,
            avvistAvNavnProvider: (UUID) -> String = { it.toString() },
            avvistAvEnhetNavnProvider: (UUID) -> String = { it.toString() },
        ): ForslagResponse {
            val forslag = decorator.forslag
            return ForslagResponse(
                id = forslag.id,
                opprettet = forslag.opprettet,
                begrunnelse = forslag.begrunnelse,
                arrangorNavn = arrangorNavn,
                endring = forslag.endring,
                status = forslag.status.toResponseStatus(
                    decorator = decorator,
                    avvistAvNavnProvider = avvistAvNavnProvider,
                    avvistAvEnhetNavnProvider = avvistAvEnhetNavnProvider,
                ),
            )
        }

        /** Lager en ForslagResponse direkte fra et Forslag */
        fun fromForslag(
            forslag: Forslag,
            arrangornavn: String,
            ansatte: GenericCache<NavAnsatt>,
            enheter: GenericCache<NavEnhet>,
        ): ForslagResponse = fromForslagDecorator(
            decorator = ForslagDecorator.DefaultDecorator(forslag),
            arrangorNavn = arrangornavn,
            avvistAvNavnProvider = { ansatte.getOrThrow(it).navn },
            avvistAvEnhetNavnProvider = { enheter.getOrThrow(it).navn },
        )
    }
}

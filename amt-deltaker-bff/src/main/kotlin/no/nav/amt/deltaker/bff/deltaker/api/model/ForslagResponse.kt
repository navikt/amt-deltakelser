package no.nav.amt.deltaker.bff.deltaker.api.model

import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.amt.lib.models.arrangor.melding.Forslag
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
) : DeltakerHistorikkResponse

@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
sealed interface ForslagResponseStatus {
    data object VenterPaSvar : ForslagResponseStatus

    data class Godkjent(
        val godkjent: LocalDateTime,
    ) : ForslagResponseStatus

    data class Avvist(
        val avvistAv: String,
        val avvistAvEnhet: String,
        val avvist: LocalDateTime,
        val begrunnelseFraNav: String,
    ) : ForslagResponseStatus

    data class Tilbakekalt(
        val tilbakekalt: LocalDateTime,
    ) : ForslagResponseStatus

    data class Erstattet(
        val erstattet: LocalDateTime,
    ) : ForslagResponseStatus
}

fun Forslag.toResponse(arrangornavn: String) = this.toResponse(arrangornavn, emptyMap(), emptyMap())

fun Forslag.toResponse(
    arrangornavn: String,
    ansatte: Map<UUID, NavAnsatt>,
    enheter: Map<UUID, NavEnhet>,
): ForslagResponse = ForslagResponse(
    id = id,
    opprettet = opprettet,
    begrunnelse = begrunnelse,
    arrangorNavn = arrangornavn,
    endring = endring,
    status = getForslagResponseStatus(ansatte, enheter),
)

private fun Forslag.getForslagResponseStatus(ansatte: Map<UUID, NavAnsatt>, enheter: Map<UUID, NavEnhet>): ForslagResponseStatus =
    when (val status = status) {
        is Forslag.Status.VenterPaSvar -> ForslagResponseStatus.VenterPaSvar
        is Forslag.Status.Godkjent -> ForslagResponseStatus.Godkjent(status.godkjent)
        is Forslag.Status.Avvist -> {
            val avvist = status
            ForslagResponseStatus.Avvist(
                avvistAv = ansatte[avvist.avvistAv.id]!!.navn,
                avvistAvEnhet = enheter[avvist.avvistAv.enhetId]!!.navn,
                avvist = avvist.avvist,
                begrunnelseFraNav = avvist.begrunnelseFraNav,
            )
        }
        is Forslag.Status.Tilbakekalt -> ForslagResponseStatus.Tilbakekalt(status.tilbakekalt)
        is Forslag.Status.Erstattet -> ForslagResponseStatus.Erstattet(status.erstattet)
    }

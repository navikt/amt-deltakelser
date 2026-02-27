package no.nav.amt.distribusjon.utils.data

import no.nav.amt.distribusjon.varsel.model.Varsel
import no.nav.amt.distribusjon.varsel.nowUTC
import java.time.ZonedDateTime
import java.util.UUID

object Varselsdata {
    fun varsel(
        type: Varsel.Type,
        status: Varsel.Status = Varsel.Status.VENTER_PA_UTSENDELSE,
        id: UUID = UUID.randomUUID(),
        hendelser: List<UUID> = listOf(UUID.randomUUID()),
        aktivFra: ZonedDateTime = nowUTC(),
        aktivTil: ZonedDateTime? = null,
        deltakerId: UUID = UUID.randomUUID(),
        personident: String = randomIdent(),
        tekst: String = "Varselstekst",
        erEksterntVarsel: Boolean = type == Varsel.Type.OPPGAVE,
        revarselForVarsel: UUID? = null,
        revarsles: ZonedDateTime? = null,
    ) = Varsel(
        id = id,
        type = type,
        hendelser = hendelser,
        status = status,
        erEksterntVarsel = erEksterntVarsel,
        aktivFra = aktivFra,
        aktivTil = aktivTil,
        deltakerId = deltakerId,
        personident = personident,
        tekst = tekst,
        revarselForVarsel = revarselForVarsel,
        revarsles = revarsles,
    )

    fun beskjed(
        status: Varsel.Status = Varsel.Status.VENTER_PA_UTSENDELSE,
        id: UUID = UUID.randomUUID(),
        hendelser: List<UUID> = listOf(UUID.randomUUID()),
        aktivFra: ZonedDateTime = Varsel.nesteUtsendingstidspunkt(),
        aktivTil: ZonedDateTime? = Varsel.nesteUtsendingstidspunkt().plus(Varsel.beskjedAktivLengde),
        deltakerId: UUID = UUID.randomUUID(),
        personident: String = randomIdent(),
        tekst: String = "Varselstekst",
        erEksterntVarsel: Boolean = true,
        revarselForVarsel: UUID? = null,
        revarsles: ZonedDateTime? = if (erEksterntVarsel) Varsel.revarslingstidspunkt() else null,
    ) = varsel(
        Varsel.Type.BESKJED,
        status,
        id,
        hendelser,
        aktivFra,
        aktivTil,
        deltakerId,
        personident,
        tekst,
        erEksterntVarsel,
        revarselForVarsel,
        revarsles,
    )
}

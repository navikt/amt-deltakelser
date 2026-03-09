package no.nav.amt.lib.testing.utils

import no.nav.amt.lib.models.deltaker.Arrangor
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.DeltakerVedImport
import no.nav.amt.lib.models.deltaker.ImportertFraArena
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

object TestData {
    fun randomOrgnr() = (900_000_000..999_999_998).random().toString()

    fun lagImportertFraArena(
        deltakerId: UUID = UUID.randomUUID(),
        importertDato: LocalDateTime = LocalDateTime.now(),
        deltakerVedImport: DeltakerVedImport = lagDeltakerVedImport(),
    ) = ImportertFraArena(
        deltakerId = deltakerId,
        importertDato = importertDato,
        deltakerVedImport = deltakerVedImport,
    )

    fun lagDeltakerVedImport(
        innsoktDato: LocalDate = LocalDate.now(),
        startdato: LocalDate? = null,
        sluttdato: LocalDate? = null,
        dagerPerUke: Float? = null,
        deltakelsesprosent: Float? = null,
        status: DeltakerStatus = DeltakerStatus(
            id = UUID.randomUUID(),
            type = DeltakerStatus.Type.DELTAR,
            aarsak = null,
            gyldigFra = LocalDateTime.now(),
            gyldigTil = null,
            opprettet = LocalDateTime.now(),
        ),
    ) = DeltakerVedImport(
        deltakerId = UUID.randomUUID(),
        innsoktDato = innsoktDato,
        startdato = startdato,
        sluttdato = sluttdato,
        dagerPerUke = dagerPerUke,
        deltakelsesprosent = deltakelsesprosent,
        status = status,
    )

    fun lagArrangor(
        id: UUID = UUID.randomUUID(),
        navn: String = "Arrangor 1",
        organisasjonsnummer: String = randomOrgnr(),
        overordnetArrangorId: UUID? = null,
    ) = Arrangor(
        id = id,
        navn = navn,
        organisasjonsnummer = organisasjonsnummer,
        overordnetArrangorId = overordnetArrangorId,
    )
}

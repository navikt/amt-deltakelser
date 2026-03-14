package no.nav.tiltaksarrangor.repositories.model

data class EndringsmeldingMedDeltakerOgDeltakerliste(
	val endringsmeldingDbo: EndringsmeldingDbo,
	val deltakerDbo: DeltakerDbo,
	val deltakerlisteDbo: DeltakerlisteDbo,
)

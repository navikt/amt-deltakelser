package no.nav.tiltaksarrangor.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.amt.lib.models.arrangor.melding.Forslag
import no.nav.tiltaksarrangor.consumer.model.EndringsmeldingType
import no.nav.tiltaksarrangor.consumer.model.Innhold
import no.nav.tiltaksarrangor.testutils.getEndringsmelding
import no.nav.tiltaksarrangor.testutils.getForslag
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class AktivEndringTest {
	@Test
	fun `getAktivEndring - ingen endringsmeldinger eller forslag - returnerer null`() {
		val deltakerId = UUID.randomUUID()
		val annenDeltakerId = UUID.randomUUID()
		val tredjeDeltakerId = UUID.randomUUID()
		val endringsmelding = getEndringsmelding(annenDeltakerId)
		val forslag = getForslag(tredjeDeltakerId)

		val aktivEndring = getAktivEndring(deltakerId, listOf(endringsmelding), listOf(forslag), true)

		aktivEndring shouldBe null
	}

	@Test
	fun `getAktivEndring - et forslag - returnerer forslag`() {
		val deltakerId = UUID.randomUUID()
		val forslag = getForslag(deltakerId)

		val aktivEndring = getAktivEndring(deltakerId, emptyList(), listOf(forslag), true)

		aktivEndring shouldNotBe null
		aktivEndring?.deltakerId shouldBe deltakerId
		aktivEndring?.sendt shouldBe forslag.opprettet.toLocalDate()
		aktivEndring?.type shouldBe AktivEndring.Type.Forslag
		aktivEndring?.endingsType shouldBe AktivEndring.EndringsType.ForlengDeltakelse
	}

	@Test
	fun `getAktivEndring - to forslag - returnerer nyeste forslag`() {
		val deltakerId = UUID.randomUUID()
		val forslag = getForslag(deltakerId)
		val eldreForslag = getForslag(deltakerId).copy(
			opprettet = LocalDateTime.now().minusWeeks(1),
			endring = Forslag.Deltakelsesmengde(
				deltakelsesprosent = 50,
				dagerPerUke = 2,
				gyldigFra = LocalDate.now(),
			),
		)

		val aktivEndring = getAktivEndring(deltakerId, emptyList(), listOf(eldreForslag, forslag), true)

		aktivEndring shouldNotBe null
		aktivEndring?.deltakerId shouldBe deltakerId
		aktivEndring?.sendt shouldBe forslag.opprettet.toLocalDate()
		aktivEndring?.type shouldBe AktivEndring.Type.Forslag
		aktivEndring?.endingsType shouldBe AktivEndring.EndringsType.ForlengDeltakelse
	}

	@Test
	fun `getAktivEndring - endringsmelding - returnerer endringsmelding`() {
		val deltakerId = UUID.randomUUID()
		val endringsmelding = getEndringsmelding(deltakerId)

		val aktivEndring = getAktivEndring(deltakerId, listOf(endringsmelding), emptyList(), false)

		aktivEndring shouldNotBe null
		aktivEndring?.deltakerId shouldBe deltakerId
		aktivEndring?.sendt shouldBe endringsmelding.sendt.toLocalDate()
		aktivEndring?.type shouldBe AktivEndring.Type.Endringsmelding
		aktivEndring?.endingsType shouldBe AktivEndring.EndringsType.ForlengDeltakelse
	}

	@Test
	fun `getAktivEndring - to endringsmeldinger - returnerer nyeste endringsmelding`() {
		val deltakerId = UUID.randomUUID()
		val endringsmelding = getEndringsmelding(deltakerId)
		val eldreEndringsmelding = getEndringsmelding(deltakerId).copy(
			type = EndringsmeldingType.ENDRE_OPPSTARTSDATO,
			innhold = Innhold.EndreOppstartsdatoInnhold(LocalDate.now()),
			sendt = LocalDateTime.now().minusWeeks(1),
		)

		val aktivEndring = getAktivEndring(deltakerId, listOf(eldreEndringsmelding, endringsmelding), emptyList(), false)

		aktivEndring shouldNotBe null
		aktivEndring?.deltakerId shouldBe deltakerId
		aktivEndring?.sendt shouldBe endringsmelding.sendt.toLocalDate()
		aktivEndring?.type shouldBe AktivEndring.Type.Endringsmelding
		aktivEndring?.endingsType shouldBe AktivEndring.EndringsType.ForlengDeltakelse
	}

	@Test
	fun `getAktivEndring - endringsmelding, komet er master - returnerer null`() {
		val deltakerId = UUID.randomUUID()
		val endringsmelding = getEndringsmelding(deltakerId)

		val aktivEndring = getAktivEndring(deltakerId, listOf(endringsmelding), emptyList(), true)

		aktivEndring shouldBe null
	}
}

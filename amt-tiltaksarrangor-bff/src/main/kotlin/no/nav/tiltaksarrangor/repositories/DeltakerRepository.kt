package no.nav.tiltaksarrangor.repositories

import no.nav.amt.lib.models.arrangor.melding.Vurdering
import no.nav.amt.lib.models.deltaker.DeltakerStatus
import no.nav.amt.lib.models.deltaker.Kilde
import no.nav.amt.lib.models.deltakerliste.GjennomforingPameldingType
import no.nav.amt.lib.models.deltakerliste.GjennomforingStatusType
import no.nav.amt.lib.models.deltakerliste.GjennomforingType
import no.nav.amt.lib.models.deltakerliste.Oppstartstype
import no.nav.amt.lib.models.deltakerliste.tiltakstype.Tiltakskode
import no.nav.tiltaksarrangor.repositories.model.DAGER_AVSLUTTET_DELTAKER_VISES
import no.nav.tiltaksarrangor.repositories.model.DeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerMedDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.DeltakerlisteDbo
import no.nav.tiltaksarrangor.utils.getNullableDouble
import no.nav.tiltaksarrangor.utils.getNullableFloat
import no.nav.tiltaksarrangor.utils.getNullableLocalDate
import no.nav.tiltaksarrangor.utils.getNullableLocalDateTime
import no.nav.tiltaksarrangor.utils.getNullableUUID
import no.nav.tiltaksarrangor.utils.objectMapper
import no.nav.tiltaksarrangor.utils.sqlParameters
import no.nav.tiltaksarrangor.utils.toPGObject
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.util.UUID

@Component
class DeltakerRepository(
	private val template: NamedParameterJdbcTemplate,
) {
	private val deltakerRowMapper =
		RowMapper { rs, _ ->
			DeltakerDbo(
				id = UUID.fromString(rs.getString("id")),
				deltakerlisteId = UUID.fromString(rs.getString("deltakerliste_id")),
				personident = rs.getString("personident"),
				fornavn = rs.getString("fornavn"),
				mellomnavn = rs.getString("mellomnavn"),
				etternavn = rs.getString("etternavn"),
				telefonnummer = rs.getString("telefonnummer"),
				epost = rs.getString("epost"),
				erSkjermet = rs.getBoolean("er_skjermet"),
				adresse = rs.getString("adresse")?.let { objectMapper.readValue(it) },
				vurderingerFraArrangor = rs.getString("vurderinger")?.let { objectMapper.readValue(it) },
				status = DeltakerStatus.Type.valueOf(rs.getString("status")),
				statusGyldigFraDato = rs.getTimestamp("status_gyldig_fra").toLocalDateTime(),
				statusOpprettetDato = rs.getTimestamp("status_opprettet_dato").toLocalDateTime(),
				statusAarsak = rs.getString("aarsak")?.let { objectMapper.readValue(it) },
				dagerPerUke = rs.getNullableFloat("dager_per_uke"),
				prosentStilling = rs.getNullableDouble("prosent_stilling"),
				startdato = rs.getNullableLocalDate("start_dato"),
				sluttdato = rs.getNullableLocalDate("slutt_dato"),
				innsoktDato = rs.getDate("innsokt_dato").toLocalDate(),
				bestillingstekst = rs.getString("bestillingstekst"),
				navKontor = rs.getString("navkontor"),
				navVeilederId = rs.getNullableUUID("navveileder_id"),
				navVeilederNavn = rs.getString("navveileder_navn"),
				navVeilederEpost = rs.getString("navveileder_epost"),
				navVeilederTelefon = rs.getString("navveileder_telefon"),
				skjultAvAnsattId = rs.getNullableUUID("skjult_av_ansatt_id"),
				skjultDato = rs.getNullableLocalDateTime("skjult_dato"),
				adressebeskyttet = rs.getBoolean("adressebeskyttet"),
				innhold = rs.getString("innhold")?.let { objectMapper.readValue(it) },
				kilde = Kilde.valueOf(rs.getString("kilde")),
				historikk = objectMapper.readValue(rs.getString("historikk")),
				sistEndret = rs.getTimestamp("modified_at").toLocalDateTime(),
				forsteVedtakFattet = rs.getNullableLocalDate("forste_vedtak_fattet"),
				erManueltDeltMedArrangor = rs.getBoolean("er_manuelt_delt_med_arrangor"),
				oppfolgingsperioder = rs.getString("oppfolgingsperioder")?.let { objectMapper.readValue(it) }
					?: emptyList(),
			)
		}

	private val deltakerMedDeltakerlisteRowMapper =
		RowMapper { rs, _ ->
			DeltakerMedDeltakerlisteDbo(
				deltaker =
					DeltakerDbo(
						id = UUID.fromString(rs.getString("deltakerid")),
						deltakerlisteId = UUID.fromString(rs.getString("deltakerliste_id")),
						personident = rs.getString("personident"),
						fornavn = rs.getString("fornavn"),
						mellomnavn = rs.getString("mellomnavn"),
						etternavn = rs.getString("etternavn"),
						telefonnummer = rs.getString("telefonnummer"),
						epost = rs.getString("epost"),
						erSkjermet = rs.getBoolean("er_skjermet"),
						adresse = rs.getString("adresse")?.let { objectMapper.readValue(it) },
						vurderingerFraArrangor = rs.getString("vurderinger")?.let { objectMapper.readValue(it) },
						status = DeltakerStatus.Type.valueOf(rs.getString("deltakerstatus")),
						statusGyldigFraDato = rs.getTimestamp("status_gyldig_fra").toLocalDateTime(),
						statusOpprettetDato = rs.getTimestamp("status_opprettet_dato").toLocalDateTime(),
						statusAarsak = rs.getString("aarsak")?.let { objectMapper.readValue(it) },
						dagerPerUke = rs.getNullableFloat("dager_per_uke"),
						prosentStilling = rs.getNullableDouble("prosent_stilling"),
						startdato = rs.getNullableLocalDate("deltaker_start_dato"),
						sluttdato = rs.getNullableLocalDate("deltaker_slutt_dato"),
						innsoktDato = rs.getDate("innsokt_dato").toLocalDate(),
						bestillingstekst = rs.getString("bestillingstekst"),
						navKontor = rs.getString("navkontor"),
						navVeilederId = rs.getNullableUUID("navveileder_id"),
						navVeilederNavn = rs.getString("navveileder_navn"),
						navVeilederEpost = rs.getString("navveileder_epost"),
						navVeilederTelefon = rs.getString("navveileder_telefon"),
						skjultAvAnsattId = rs.getNullableUUID("skjult_av_ansatt_id"),
						skjultDato = rs.getNullableLocalDateTime("skjult_dato"),
						adressebeskyttet = rs.getBoolean("adressebeskyttet"),
						innhold = rs.getString("innhold")?.let { objectMapper.readValue(it) },
						kilde = Kilde.valueOf(rs.getString("kilde")),
						historikk = objectMapper.readValue(rs.getString("historikk")),
						sistEndret = rs.getTimestamp("modified_at").toLocalDateTime(),
						forsteVedtakFattet = rs.getNullableLocalDate("forste_vedtak_fattet"),
						erManueltDeltMedArrangor = rs.getBoolean("er_manuelt_delt_med_arrangor"),
						oppfolgingsperioder = rs.getString("oppfolgingsperioder")?.let { objectMapper.readValue(it) }
							?: emptyList(),
					),
				deltakerliste =
					DeltakerlisteDbo(
						id = UUID.fromString(rs.getString("deltakerliste_id")),
						navn = rs.getString("navn"),
						gjennomforingstype = GjennomforingType.valueOf(rs.getString("gjennomforingstype")),
						status = GjennomforingStatusType.valueOf(rs.getString("deltakerliste_status")),
						arrangorId = UUID.fromString(rs.getString("arrangor_id")),
						tiltaksnavn = rs.getString("tiltaksnavn"),
						tiltakskode = rs.getString("tiltakskode").let { Tiltakskode.valueOf(it) },
						startDato = rs.getNullableLocalDate("deltakerliste_start_dato"),
						sluttDato = rs.getNullableLocalDate("deltakerliste_slutt_dato"),
						erKurs = rs.getBoolean("er_kurs"),
						oppstartstype = Oppstartstype.valueOf(rs.getString("oppstartstype")),
						tilgjengeligForArrangorFraOgMedDato = rs.getNullableLocalDate("tilgjengelig_fom"),
						pameldingstype = rs.getString("pameldingstype")?.let { GjennomforingPameldingType.valueOf(it) },
					),
			)
		}

	fun insertOrUpdateDeltaker(deltakerDbo: DeltakerDbo) {
		val sql =
			"""
			INSERT INTO deltaker(
				id, deltakerliste_id, personident, fornavn, mellomnavn, etternavn, telefonnummer, epost,
				er_skjermet, status, status_gyldig_fra, status_opprettet_dato, aarsak, dager_per_uke, prosent_stilling,
				start_dato, slutt_dato,
				innsokt_dato, bestillingstekst, navkontor, navveileder_id,
				skjult_av_ansatt_id, skjult_dato, adresse, vurderinger, adressebeskyttet,
				innhold, kilde, historikk, modified_at, forste_vedtak_fattet, er_manuelt_delt_med_arrangor, oppfolgingsperioder)
			VALUES (
				:id,
				:deltakerliste_id,
				:personident,
				:fornavn,
				:mellomnavn,
				:etternavn,
				:telefonnummer,
				:epost,
				:er_skjermet,
				:status,
				:status_gyldig_fra,
				:status_opprettet_dato,
				:aarsak,
				:dager_per_uke,
				:prosent_stilling,
				:start_dato,
				:slutt_dato,
				:innsokt_dato,
				:bestillingstekst,
				:navkontor,
				:navveileder_id,
				:skjult_av_ansatt_id,
				:skjult_dato,
				:adresse,
				:vurderinger,
				:adressebeskyttet,
				:innhold,
				:kilde,
				:historikk,
				:modified_at,
				:forste_vedtak_fattet,
				:er_manuelt_delt_med_arrangor,
				:oppfolgingsperioder)
			ON CONFLICT (id) DO UPDATE SET
				deltakerliste_id      = :deltakerliste_id,
				personident           = :personident,
				fornavn               = :fornavn,
				mellomnavn            = :mellomnavn,
				etternavn             = :etternavn,
				telefonnummer         = :telefonnummer,
				epost                 = :epost,
				er_skjermet           = :er_skjermet,
				status                = :status,
				status_gyldig_fra     = :status_gyldig_fra,
				status_opprettet_dato = :status_opprettet_dato,
				aarsak				  = :aarsak,
				dager_per_uke         = :dager_per_uke,
				prosent_stilling      = :prosent_stilling,
				start_dato            = :start_dato,
				slutt_dato            = :slutt_dato,
				innsokt_dato          = :innsokt_dato,
				bestillingstekst      = :bestillingstekst,
				navkontor             = :navkontor,
				navveileder_id        = :navveileder_id,
				skjult_av_ansatt_id   = :skjult_av_ansatt_id,
				skjult_dato           = :skjult_dato,
				adresse				  = :adresse,
				vurderinger			  = :vurderinger,
				adressebeskyttet	  = :adressebeskyttet,
				innhold 			  = :innhold,
				kilde		 		  = :kilde,
				historikk             = :historikk,
				modified_at           = :modified_at,
				forste_vedtak_fattet  = :forste_vedtak_fattet,
				er_manuelt_delt_med_arrangor = :er_manuelt_delt_med_arrangor,
				oppfolgingsperioder = :oppfolgingsperioder
			""".trimIndent()

		template.update(
			sql,
			sqlParameters(
				"id" to deltakerDbo.id,
				"deltakerliste_id" to deltakerDbo.deltakerlisteId,
				"personident" to deltakerDbo.personident,
				"fornavn" to deltakerDbo.fornavn,
				"mellomnavn" to deltakerDbo.mellomnavn,
				"etternavn" to deltakerDbo.etternavn,
				"telefonnummer" to deltakerDbo.telefonnummer,
				"epost" to deltakerDbo.epost,
				"er_skjermet" to deltakerDbo.erSkjermet,
				"status" to deltakerDbo.status.name,
				"status_gyldig_fra" to deltakerDbo.statusGyldigFraDato,
				"status_opprettet_dato" to deltakerDbo.statusOpprettetDato,
				"aarsak" to toPGObject(deltakerDbo.statusAarsak),
				"dager_per_uke" to deltakerDbo.dagerPerUke,
				"prosent_stilling" to deltakerDbo.prosentStilling,
				"start_dato" to deltakerDbo.startdato,
				"slutt_dato" to deltakerDbo.sluttdato,
				"innsokt_dato" to deltakerDbo.innsoktDato,
				"bestillingstekst" to deltakerDbo.bestillingstekst,
				"navkontor" to deltakerDbo.navKontor,
				"navveileder_id" to deltakerDbo.navVeilederId,
				"skjult_av_ansatt_id" to deltakerDbo.skjultAvAnsattId,
				"skjult_dato" to deltakerDbo.skjultDato,
				"adresse" to deltakerDbo.adresse?.toPGObject(),
				"vurderinger" to toPGObject(deltakerDbo.vurderingerFraArrangor),
				"adressebeskyttet" to deltakerDbo.adressebeskyttet,
				"innhold" to toPGObject(deltakerDbo.innhold),
				"kilde" to deltakerDbo.kilde?.name,
				"historikk" to toPGObject(deltakerDbo.historikk),
				"modified_at" to deltakerDbo.sistEndret,
				"forste_vedtak_fattet" to deltakerDbo.forsteVedtakFattet,
				"er_manuelt_delt_med_arrangor" to deltakerDbo.erManueltDeltMedArrangor,
				"oppfolgingsperioder" to toPGObject(deltakerDbo.oppfolgingsperioder),
			),
		)
	}

	fun deleteDeltaker(deltakerId: UUID): Int {
		template.update(
			"DELETE FROM veileder_deltaker WHERE deltaker_id = :deltaker_id",
			sqlParameters("deltaker_id" to deltakerId),
		)
		template.update(
			"DELETE FROM endringsmelding WHERE deltaker_id = :deltaker_id",
			sqlParameters("deltaker_id" to deltakerId),
		)
		template.update(
			"DELETE FROM forslag WHERE deltaker_id = :deltaker_id",
			sqlParameters("deltaker_id" to deltakerId),
		)
		template.update(
			"DELETE FROM ulest_endring WHERE deltaker_id = :deltaker_id",
			sqlParameters("deltaker_id" to deltakerId),
		)
		return template.update(
			"DELETE FROM deltaker WHERE id = :id",
			sqlParameters("id" to deltakerId),
		)
	}

	fun getDeltaker(deltakerId: UUID): DeltakerDbo? = template
		.query(
			"""
			SELECT
				deltaker.*,
				nav_ansatt.navn AS navveileder_navn,
				nav_ansatt.epost AS navveileder_epost,
				nav_ansatt.telefon AS navveileder_telefon
			FROM
				deltaker
				LEFT JOIN nav_ansatt ON deltaker.navveileder_id = nav_ansatt.id
			WHERE
				deltaker.id = :id
			""".trimIndent(),
			sqlParameters("id" to deltakerId),
			deltakerRowMapper,
		).firstOrNull()

	/**
	 * Henter deltakere for en deltakerliste, filtrert på skjuling og slutt-dato.
	 *
	 * NB:
	 *  - Denne metoden returnerer *ikke* informasjon om NAV-veileder.
	 *  - Felt som navVeilederNavn/etc. fylles ikke ut her.
	 *
	 * Årsak:
	 *  - For å redusere kompleksitet og gjøre spørringen raskere,
	 *    gjøres det ikke JOIN mot tabellen `nav_ansatt`.
	 */
	fun getDeltakereForDeltakerliste(deltakerlisteId: UUID): List<DeltakerDbo> = template
		.query(
			"""
			SELECT
				deltaker.*,
				NULL AS navveileder_navn,
				NULL AS navveileder_epost,
				NULL AS navveileder_telefon
			FROM
				deltaker
			WHERE
				deltaker.deltakerliste_id = :deltakerliste_id
				-- erstatter erSkjult()
				AND deltaker.skjult_dato IS NULL
				-- erstatter skalVises()
			  	AND (deltaker.slutt_dato IS NULL OR deltaker.slutt_dato > :min_sluttdato_for_visning)
			""".trimIndent(),
			sqlParameters(
				"deltakerliste_id" to deltakerlisteId,
				"min_sluttdato_for_visning" to LocalDate.now().minusDays(DAGER_AVSLUTTET_DELTAKER_VISES),
			),
			deltakerRowMapper,
		)

	fun getDeltakereMedDeltakerliste(deltakerIder: List<UUID>): List<DeltakerMedDeltakerlisteDbo> = template.query(
		"""
		SELECT
			deltaker.id as deltakerid,
			deltakerliste_id,
			personident,
			fornavn,
			mellomnavn,
			etternavn,
			telefonnummer,
			deltaker.epost,
			er_skjermet,
			adresse,
			vurderinger,
			deltaker.status as deltakerstatus,
			status_gyldig_fra,
			status_opprettet_dato,
			aarsak,
			dager_per_uke,
			prosent_stilling,
			deltaker.start_dato as deltaker_start_dato,
			deltaker.slutt_dato as deltaker_slutt_dato,
			innsokt_dato,
			bestillingstekst,
			innhold,
			kilde,
			historikk,
			navkontor,

			navveileder_id,
			nav_ansatt.navn AS navveileder_navn,
			nav_ansatt.epost AS navveileder_epost,
			nav_ansatt.telefon AS navveileder_telefon,

			skjult_av_ansatt_id,
			skjult_dato,
			adressebeskyttet,
			deltakerliste.navn,
			deltakerliste.gjennomforingstype,
			deltakerliste.status as deltakerliste_status,
			arrangor_id,
			tiltaksnavn,
			tiltakskode,
			deltakerliste.start_dato as deltakerliste_start_dato,
			deltakerliste.slutt_dato as deltakerliste_slutt_dato,
			er_kurs,
			oppstartstype,
			tilgjengelig_fom,
			pameldingstype,

			deltaker.modified_at as modified_at,
			forste_vedtak_fattet,
			er_manuelt_delt_med_arrangor,
			oppfolgingsperioder
		FROM
			deltaker
			JOIN deltakerliste ON deltakerliste.id = deltaker.deltakerliste_id
			LEFT JOIN nav_ansatt ON deltaker.navveileder_id = nav_ansatt.id
		WHERE
			deltaker.id IN (:ids);
		""".trimIndent(),
		sqlParameters("ids" to deltakerIder),
		deltakerMedDeltakerlisteRowMapper,
	)

	fun getDeltakerMedDeltakerliste(deltakerId: UUID): DeltakerMedDeltakerlisteDbo? = template
		.query(
			"""
			SELECT
				deltaker.id as deltakerid,
				deltakerliste_id,
				personident,
				fornavn,
				mellomnavn,
				etternavn,
				telefonnummer,
				deltaker.epost,
				er_skjermet,
				adresse,
				vurderinger,
				deltaker.status as deltakerstatus,
				status_gyldig_fra,
				status_opprettet_dato,
				aarsak,
				dager_per_uke,
				prosent_stilling,
				deltaker.start_dato as deltaker_start_dato,
				deltaker.slutt_dato as deltaker_slutt_dato,
				innsokt_dato,
				bestillingstekst,
				innhold,
				kilde,
				historikk,
				navkontor,

				navveileder_id,
				nav_ansatt.navn AS navveileder_navn,
				nav_ansatt.epost AS navveileder_epost,
				nav_ansatt.telefon AS navveileder_telefon,

				skjult_av_ansatt_id,
				skjult_dato,
				adressebeskyttet,
				deltakerliste.navn,
				deltakerliste.gjennomforingstype,
				deltakerliste.status as deltakerliste_status,
				arrangor_id,
				tiltaksnavn,
				tiltakskode,
				deltakerliste.start_dato as deltakerliste_start_dato,
				deltakerliste.slutt_dato as deltakerliste_slutt_dato,
				er_kurs,
				oppstartstype,
				tilgjengelig_fom,
				pameldingstype,

				deltaker.modified_at as modified_at,
				forste_vedtak_fattet,
				er_manuelt_delt_med_arrangor,
				oppfolgingsperioder
			FROM
				deltaker
			 	JOIN deltakerliste ON deltakerliste.id = deltaker.deltakerliste_id
			 	LEFT JOIN nav_ansatt ON deltaker.navveileder_id = nav_ansatt.id
			WHERE
				deltaker.id = :id;
			""".trimIndent(),
			sqlParameters("id" to deltakerId),
			deltakerMedDeltakerlisteRowMapper,
		).firstOrNull()

	fun deleteDeltakereForDeltakerliste(deltakerlisteId: UUID): Int {
		val deltakereSomSkalSlettes =
			template.query(
				"SELECT id FROM deltaker WHERE deltakerliste_id = :deltakerliste_id",
				sqlParameters("deltakerliste_id" to deltakerlisteId),
			) { rs, _ ->
				UUID.fromString(rs.getString("id"))
			}
		deltakereSomSkalSlettes.forEach { deleteDeltaker(it) }
		return deltakereSomSkalSlettes.size
	}

	fun getDeltakereSomSkalSlettes(slettesDato: LocalDate): List<UUID> = template.query(
		"""
		SELECT id
		FROM deltaker
		WHERE status IN ('HAR_SLUTTET','IKKE_AKTUELL','AVBRUTT','FULLFORT') AND status_gyldig_fra < :slettesDato
		""".trimIndent(),
		sqlParameters("slettesDato" to slettesDato),
	) { rs, _ ->
		UUID.fromString(rs.getString("id"))
	}

	fun skjulDeltaker(deltakerId: UUID, ansattId: UUID) {
		val sql =
			"""
			UPDATE deltaker SET skjult_dato = CURRENT_TIMESTAMP, skjult_av_ansatt_id = :ansattId WHERE id = :deltakerId
			""".trimIndent()

		template.update(
			sql,
			sqlParameters(
				"ansattId" to ansattId,
				"deltakerId" to deltakerId,
			),
		)
	}

	fun oppdaterVurderingerForDeltaker(deltakerId: UUID, oppdaterteVurderinger: List<Vurdering>) {
		val sql =
			"""
			UPDATE deltaker SET vurderinger = :vurderinger WHERE id = :deltakerId
			""".trimIndent()

		template.update(
			sql,
			sqlParameters(
				"vurderinger" to toPGObject(oppdaterteVurderinger),
				"deltakerId" to deltakerId,
			),
		)
	}

	fun oppdaterEnhetsnavnForDeltakere(opprinneligEnhetsnavn: String, nyttEnhetsnavn: String) {
		val sql =
			"""
			update deltaker
			set navkontor = :nyttEnhetsnavn
			where navkontor = :opprinneligEnhetsnavn
			""".trimIndent()

		val params = sqlParameters("nyttEnhetsnavn" to nyttEnhetsnavn, "opprinneligEnhetsnavn" to opprinneligEnhetsnavn)
		template.update(sql, params)
	}
}

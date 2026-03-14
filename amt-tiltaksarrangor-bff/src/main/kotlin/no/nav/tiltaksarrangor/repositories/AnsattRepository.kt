package no.nav.tiltaksarrangor.repositories

import no.nav.tiltaksarrangor.consumer.model.AnsattRolle
import no.nav.tiltaksarrangor.model.Veiledertype
import no.nav.tiltaksarrangor.repositories.model.AnsattDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattPersonaliaDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattRolleMedAnsattIdDbo
import no.nav.tiltaksarrangor.repositories.model.AnsattVeilederDbo
import no.nav.tiltaksarrangor.repositories.model.KoordinatorDeltakerlisteDbo
import no.nav.tiltaksarrangor.repositories.model.VeilederDeltakerDbo
import no.nav.tiltaksarrangor.repositories.model.VeilederForDeltakerDbo
import no.nav.tiltaksarrangor.utils.sqlParameters
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
class AnsattRepository(
	private val template: NamedParameterJdbcTemplate,
) {
	private val ansattPersonaliaRowMapper =
		RowMapper { rs, _ ->
			AnsattPersonaliaDbo(
				id = UUID.fromString(rs.getString("id")),
				personIdent = rs.getString("personident"),
				fornavn = rs.getString("fornavn"),
				mellomnavn = rs.getString("mellomnavn"),
				etternavn = rs.getString("etternavn"),
			)
		}

	private val veilederDeltakerRowMapper =
		RowMapper { rs, _ ->
			VeilederDeltakerDbo(
				deltakerId = UUID.fromString(rs.getString("deltaker_id")),
				veilederType = Veiledertype.valueOf(rs.getString("veiledertype")),
			)
		}

	private val koordinatorDeltakerlisterRowMapper =
		RowMapper { rs, _ ->
			KoordinatorDeltakerlisteDbo(
				deltakerlisteId = UUID.fromString(rs.getString("deltakerliste_id")),
			)
		}

	private val ansattRolleRowMapper =
		RowMapper { rs, _ ->
			AnsattRolleDbo(
				arrangorId = UUID.fromString(rs.getString("arrangor_id")),
				rolle = AnsattRolle.valueOf(rs.getString("rolle")),
			)
		}

	private val ansattRolleMedAnsattIdRowMapper =
		RowMapper { rs, _ ->
			AnsattRolleMedAnsattIdDbo(
				ansattId = UUID.fromString(rs.getString("ansatt_id")),
				ansattRolleDbo =
					AnsattRolleDbo(
						arrangorId = UUID.fromString(rs.getString("arrangor_id")),
						rolle = AnsattRolle.valueOf(rs.getString("rolle")),
					),
			)
		}

	private val ansattVeilederRowMapper =
		RowMapper { rs, _ ->
			AnsattVeilederDbo(
				ansattPersonaliaDbo =
					AnsattPersonaliaDbo(
						id = UUID.fromString(rs.getString("id")),
						personIdent = rs.getString("personident"),
						fornavn = rs.getString("fornavn"),
						mellomnavn = rs.getString("mellomnavn"),
						etternavn = rs.getString("etternavn"),
					),
				veilederDeltakerDbo =
					VeilederDeltakerDbo(
						deltakerId = UUID.fromString(rs.getString("deltaker_id")),
						veilederType = Veiledertype.valueOf(rs.getString("veiledertype")),
					),
			)
		}

	fun updateSistInnlogget(ansattId: UUID) {
		val sql =
			"""
			UPDATE ansatt SET sist_innlogget = CURRENT_TIMESTAMP WHERE id = :ansattId
			""".trimIndent()

		template.update(sql, sqlParameters("ansattId" to ansattId))
	}

	fun insertKoordinatorDeltakerliste(ansattId: UUID, deltakerliste: KoordinatorDeltakerlisteDbo) {
		val sql =
			"""
			INSERT INTO koordinator_deltakerliste(ansatt_id, deltakerliste_id)
			VALUES (:ansatt_id,
					:deltakerliste_id)
			ON CONFLICT (ansatt_id, deltakerliste_id) DO NOTHING
			""".trimIndent()

		template.update(
			sql,
			sqlParameters(
				"ansatt_id" to ansattId,
				"deltakerliste_id" to deltakerliste.deltakerlisteId,
			),
		)
	}

	fun updateVeiledereForDeltaker(deltakerId: UUID, veiledere: List<VeilederForDeltakerDbo>) {
		template.update(
			"DELETE FROM veileder_deltaker WHERE deltaker_id = :deltaker_id",
			sqlParameters("deltaker_id" to deltakerId),
		)
		veiledere.forEach {
			val sql =
				"""
				INSERT INTO veileder_deltaker(ansatt_id, deltaker_id, veiledertype)
				VALUES (:ansatt_id,
						:deltaker_id,
						:veiledertype)
				ON CONFLICT (ansatt_id, deltaker_id) DO UPDATE SET
						ansatt_id 		= :ansatt_id,
						deltaker_id		= :deltaker_id,
						veiledertype 	= :veiledertype
				""".trimIndent()

			template.update(
				sql,
				sqlParameters(
					"ansatt_id" to it.ansattId,
					"deltaker_id" to deltakerId,
					"veiledertype" to it.veilederType.name,
				),
			)
		}
	}

	fun deleteKoordinatorDeltakerliste(ansattId: UUID, deltakerliste: KoordinatorDeltakerlisteDbo) {
		val sql =
			"""
			DELETE FROM koordinator_deltakerliste WHERE ansatt_id = :ansatt_id AND deltakerliste_id = :deltakerliste_id
			""".trimIndent()

		template.update(
			sql,
			sqlParameters(
				"ansatt_id" to ansattId,
				"deltakerliste_id" to deltakerliste.deltakerlisteId,
			),
		)
	}

	@Retryable(
		includes = [PessimisticLockingFailureException::class],
		maxRetries = 2,
		delay = 250,
		multiplier = 2.0,
		jitter = 250,
	)
	@Transactional
	fun insertOrUpdateAnsatt(ansattDbo: AnsattDbo) {
		val sql =
			"""
			INSERT INTO ansatt(id, personident, fornavn, mellomnavn, etternavn)
			VALUES (:id,
					:personident,
					:fornavn,
					:mellomnavn,
					:etternavn)
			ON CONFLICT (id) DO UPDATE SET
					personident 		= :personident,
					fornavn				= :fornavn,
					mellomnavn 			= :mellomnavn,
					etternavn			= :etternavn
			""".trimIndent()

		template.update(
			sql,
			sqlParameters(
				"id" to ansattDbo.id,
				"personident" to ansattDbo.personIdent,
				"fornavn" to ansattDbo.fornavn,
				"mellomnavn" to ansattDbo.mellomnavn,
				"etternavn" to ansattDbo.etternavn,
			),
		)

		insertOrUpdateAnsattRolle(ansattDbo.id, ansattDbo.roller)
		insertOrUpdateKoordinatorDeltakerliste(ansattDbo.id, ansattDbo.deltakerlister)
		insertOrUpdateVeilederDeltaker(ansattDbo.id, ansattDbo.veilederDeltakere.distinct())
	}

	private fun insertOrUpdateAnsattRolle(ansattId: UUID, roller: List<AnsattRolleDbo>) {
		template.update(
			"DELETE FROM ansatt_rolle WHERE ansatt_id = :ansatt_id",
			sqlParameters("ansatt_id" to ansattId),
		)
		roller.forEach {
			val sql =
				"""
				INSERT INTO ansatt_rolle(ansatt_id, arrangor_id, rolle)
				VALUES (:ansatt_id,
						:arrangor_id,
						:rolle)
				ON CONFLICT (ansatt_id, arrangor_id, rolle) DO UPDATE SET
						ansatt_id 		= :ansatt_id,
						arrangor_id		= :arrangor_id,
						rolle 			= :rolle
				""".trimIndent()

			template.update(
				sql,
				sqlParameters(
					"ansatt_id" to ansattId,
					"arrangor_id" to it.arrangorId,
					"rolle" to it.rolle.name,
				),
			)
		}
	}

	private fun insertOrUpdateKoordinatorDeltakerliste(ansattId: UUID, deltakerlister: List<KoordinatorDeltakerlisteDbo>) {
		template.update(
			"DELETE FROM koordinator_deltakerliste WHERE ansatt_id = :ansatt_id",
			sqlParameters("ansatt_id" to ansattId),
		)
		deltakerlister.forEach {
			val sql =
				"""
				INSERT INTO koordinator_deltakerliste(ansatt_id, deltakerliste_id)
				VALUES (:ansatt_id,
						:deltakerliste_id)
				ON CONFLICT (ansatt_id, deltakerliste_id) DO NOTHING
				""".trimIndent()

			template.update(
				sql,
				sqlParameters(
					"ansatt_id" to ansattId,
					"deltakerliste_id" to it.deltakerlisteId,
				),
			)
		}
	}

	private fun insertOrUpdateVeilederDeltaker(ansattId: UUID, veilederDeltakere: List<VeilederDeltakerDbo>) {
		template.update(
			"DELETE FROM veileder_deltaker WHERE ansatt_id = :ansatt_id",
			sqlParameters("ansatt_id" to ansattId),
		)
		veilederDeltakere.forEach {
			val sql =
				"""
				INSERT INTO veileder_deltaker(ansatt_id, deltaker_id, veiledertype)
				VALUES (:ansatt_id,
						:deltaker_id,
						:veiledertype)
				ON CONFLICT (ansatt_id, deltaker_id) DO UPDATE SET
					ansatt_id 		= :ansatt_id,
					deltaker_id		= :deltaker_id,
					veiledertype 	= :veiledertype
				""".trimIndent()

			template.update(
				sql,
				sqlParameters(
					"ansatt_id" to ansattId,
					"deltaker_id" to it.deltakerId,
					"veiledertype" to it.veilederType.name,
				),
			)
		}
	}

	fun deleteAnsatt(ansattId: UUID): Int = template.update(
		"DELETE FROM ansatt WHERE id = :id",
		sqlParameters("id" to ansattId),
	)

	fun getAnsatt(ansattId: UUID): AnsattDbo? {
		val ansattRolleListe = getAnsattRolleListe(ansattId)
		val koordinatorDeltakerlisteDboListe = getKoordinatorDeltakerlisteDboListe(ansattId)
		val veilederDeltakerDboListe = getVeilederDeltakerDboListe(ansattId)
		val ansattPersonaliaDbo = getAnsattPersonaliaDbo(ansattId)

		return ansattPersonaliaDbo?.let {
			AnsattDbo(
				id = it.id,
				personIdent = it.personIdent,
				fornavn = it.fornavn,
				mellomnavn = it.mellomnavn,
				etternavn = it.etternavn,
				roller = ansattRolleListe,
				deltakerlister = koordinatorDeltakerlisteDboListe,
				veilederDeltakere = veilederDeltakerDboListe,
			)
		}
	}

	fun getAnsatt(personIdent: String): AnsattDbo? {
		val ansattPersonaliaDbo = getAnsattPersonaliaDbo(personIdent) ?: return null
		val ansattRolleListe = getAnsattRolleListe(ansattPersonaliaDbo.id)
		val unikeRoller = ansattRolleListe.map { it.rolle }.distinct()

		val koordinatorDeltakerlisteDboListe =
			unikeRoller
				.find { it == AnsattRolle.KOORDINATOR }
				?.let { getKoordinatorDeltakerlisteDboListe(ansattPersonaliaDbo.id) } ?: emptyList()
		val veilederDeltakerDboListe =
			unikeRoller
				.find { it == AnsattRolle.VEILEDER }
				?.let { getVeilederDeltakerDboListe(ansattPersonaliaDbo.id) } ?: emptyList()

		return AnsattDbo(
			id = ansattPersonaliaDbo.id,
			personIdent = ansattPersonaliaDbo.personIdent,
			fornavn = ansattPersonaliaDbo.fornavn,
			mellomnavn = ansattPersonaliaDbo.mellomnavn,
			etternavn = ansattPersonaliaDbo.etternavn,
			roller = ansattRolleListe,
			deltakerlister = koordinatorDeltakerlisteDboListe,
			veilederDeltakere = veilederDeltakerDboListe,
		)
	}

	fun getVeiledereForDeltaker(deltakerId: UUID): List<AnsattVeilederDbo> = template.query(
		"""
		SELECT *
		FROM veileder_deltaker
				 INNER JOIN ansatt a ON a.id = veileder_deltaker.ansatt_id
		WHERE veileder_deltaker.deltaker_id = :deltaker_id;
		""".trimIndent(),
		sqlParameters("deltaker_id" to deltakerId),
		ansattVeilederRowMapper,
	)

	fun getVeiledereForDeltakere(deltakerIder: List<UUID>): List<AnsattVeilederDbo> = template.query(
		"""
		SELECT *
		FROM veileder_deltaker
				 INNER JOIN ansatt a ON a.id = veileder_deltaker.ansatt_id
		WHERE veileder_deltaker.deltaker_id in(:deltaker_ids);
		""".trimIndent(),
		sqlParameters("deltaker_ids" to deltakerIder),
		ansattVeilederRowMapper,
	)

	fun getKoordinatorerForDeltakerliste(deltakerlisteId: UUID, arrangorId: UUID): List<AnsattPersonaliaDbo> = template.query(
		"""
		SELECT distinct a.*
		FROM ansatt a
				 INNER JOIN ansatt_rolle ar on a.id = ar.ansatt_id
				 INNER JOIN koordinator_deltakerliste kdl on ar.ansatt_id = kdl.ansatt_id
		WHERE kdl.deltakerliste_id = :deltakerliste_id
		  AND ar.arrangor_id = :arrangor_id
		  AND ar.rolle = :rolle;
		""".trimIndent(),
		sqlParameters(
			"deltakerliste_id" to deltakerlisteId,
			"arrangor_id" to arrangorId,
			"rolle" to AnsattRolle.KOORDINATOR.name,
		),
		ansattPersonaliaRowMapper,
	)

	fun getVeiledereForArrangor(arrangorId: UUID): List<AnsattPersonaliaDbo> = template.query(
		"""
		SELECT distinct a.*
		FROM ansatt a
				 INNER JOIN ansatt_rolle ar on a.id = ar.ansatt_id
		WHERE ar.arrangor_id = :arrangor_id
		  AND ar.rolle = :rolle;
		""".trimIndent(),
		sqlParameters(
			"arrangor_id" to arrangorId,
			"rolle" to AnsattRolle.VEILEDER.name,
		),
		ansattPersonaliaRowMapper,
	)

	fun getAnsattRolleListe(ansattId: UUID): List<AnsattRolleDbo> = template.query(
		"SELECT * FROM ansatt_rolle WHERE ansatt_id = :ansatt_id",
		sqlParameters("ansatt_id" to ansattId),
		ansattRolleRowMapper,
	)

	fun getAnsattRolleLister(ansattIder: List<UUID>): List<AnsattRolleMedAnsattIdDbo> = template.query(
		"SELECT * FROM ansatt_rolle WHERE ansatt_id in(:ansatt_ids)",
		sqlParameters("ansatt_ids" to ansattIder),
		ansattRolleMedAnsattIdRowMapper,
	)

	fun getKoordinatorDeltakerlisteDboListe(ansattId: UUID): List<KoordinatorDeltakerlisteDbo> = template.query(
		"SELECT * FROM koordinator_deltakerliste WHERE ansatt_id = :ansatt_id",
		sqlParameters("ansatt_id" to ansattId),
		koordinatorDeltakerlisterRowMapper,
	)

	fun getVeilederDeltakerDboListe(ansattId: UUID): List<VeilederDeltakerDbo> = template.query(
		"""
		SELECT *
		FROM veileder_deltaker
		         INNER JOIN deltaker d ON d.id = veileder_deltaker.deltaker_id
		WHERE skjult_dato is NULL AND ansatt_id = :ansatt_id;
		""".trimIndent(),
		sqlParameters("ansatt_id" to ansattId),
		veilederDeltakerRowMapper,
	)

	private fun getAnsattPersonaliaDbo(personIdent: String): AnsattPersonaliaDbo? = template
		.query(
			"SELECT * FROM ansatt WHERE personident = :personIdent",
			sqlParameters("personIdent" to personIdent),
			ansattPersonaliaRowMapper,
		).firstOrNull()

	private fun getAnsattPersonaliaDbo(ansattId: UUID): AnsattPersonaliaDbo? = template
		.query(
			"SELECT * FROM ansatt WHERE id = :id",
			sqlParameters("id" to ansattId),
			ansattPersonaliaRowMapper,
		).firstOrNull()
}

package no.nav.tiltaksarrangor.service

import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.common.audit_log.cef.CefMessageSeverity
import no.nav.common.audit_log.log.AuditLogger
import no.nav.tiltaksarrangor.repositories.ArrangorRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class AuditLoggerService(
	private val auditLogger: AuditLogger,
	private val arrangorRepository: ArrangorRepository,
) {
	companion object {
		const val APPLICATION_NAME = "amt-tiltaksarrangor-bff"
		const val AUDIT_LOG_NAME = "Sporingslogg"
		const val MESSAGE_EXTENSION = "msg"

		const val TILTAKSARRANGOR_ANSATT_DELTAKER_OPPSLAG_AUDIT_LOG_REASON =
			"Tiltaksarrangor ansatt har gjort oppslag paa deltaker."
	}

	fun sendAuditLog(
		ansattPersonIdent: String,
		deltakerPersonIdent: String,
		arrangorId: UUID,
	) {
		val arrangorOrgnummer =
			arrangorRepository.getArrangor(arrangorId)?.organisasjonsnummer
				?: throw IllegalStateException("Fant ikke organisasjonsnummer for arrangørId $arrangorId")
		sendAuditLog(
			ansattPersonIdent = ansattPersonIdent,
			deltakerPersonIdent = deltakerPersonIdent,
			arrangorOrgnummer = arrangorOrgnummer,
		)
	}

	private fun sendAuditLog(
		ansattPersonIdent: String,
		deltakerPersonIdent: String,
		arrangorOrgnummer: String,
	) {
		val extensions = mapOf("cn1" to arrangorOrgnummer)

		val builder =
			CefMessage
				.builder()
				.applicationName(APPLICATION_NAME)
				.event(CefMessageEvent.ACCESS)
				.name(AUDIT_LOG_NAME)
				.severity(CefMessageSeverity.INFO)
				.sourceUserId(ansattPersonIdent)
				.destinationUserId(deltakerPersonIdent)
				.timeEnded(System.currentTimeMillis())
				.extension(MESSAGE_EXTENSION, TILTAKSARRANGOR_ANSATT_DELTAKER_OPPSLAG_AUDIT_LOG_REASON)

		extensions.forEach {
			builder.extension(it.key, it.value)
		}

		val msg = builder.build()

		auditLogger.log(msg)
	}
}

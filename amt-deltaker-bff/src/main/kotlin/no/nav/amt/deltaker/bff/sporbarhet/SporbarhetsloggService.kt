package no.nav.amt.deltaker.bff.sporbarhet

import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.cef.CefMessageEvent
import no.nav.common.audit_log.cef.CefMessageSeverity
import no.nav.common.audit_log.log.AuditLogger

class SporbarhetsloggService(
    private val auditLogger: AuditLogger,
) {
    companion object {
        const val APPLICATION_NAME = "amt-deltaker-bff"
        const val AUDIT_LOG_NAME = "Sporingslogg"
        const val MESSAGE_EXTENSION = "msg"

        const val NAVANSATT_DELTAKER_OPPSLAG_AUDIT_LOG_REASON = "Nav-ansatt har gjort oppslag på deltaker."
    }

    fun sendAuditLog(
        navIdent: String,
        deltakerPersonIdent: String,
    ) {
        val builder =
            CefMessage
                .builder()
                .applicationName(APPLICATION_NAME)
                .event(CefMessageEvent.ACCESS)
                .name(AUDIT_LOG_NAME)
                .severity(CefMessageSeverity.INFO)
                .sourceUserId(navIdent)
                .destinationUserId(deltakerPersonIdent)
                .timeEnded(System.currentTimeMillis())
                .extension(MESSAGE_EXTENSION, NAVANSATT_DELTAKER_OPPSLAG_AUDIT_LOG_REASON)

        val msg = builder.build()

        auditLogger.log(msg)
    }
}

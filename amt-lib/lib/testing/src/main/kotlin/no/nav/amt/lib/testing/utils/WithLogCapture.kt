package no.nav.amt.deltaker.bff.utils

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import kotlin.apply

/**
 * Captures log messages emitted by the specified logger during execution of [block].
 *
 * @param loggerName The fully qualified logger name
 * @param block The suspend function block to run while capturing logs.
 */
fun withLogCapture(loggerName: String, block: suspend (List<ILoggingEvent>) -> Unit) {
    val logger = LoggerFactory.getLogger(loggerName) as Logger
    val appender = ListAppender<ILoggingEvent>().apply {
        start()
        logger.addAppender(this)
    }

    try {
        runBlocking { block(appender.list) }
    } finally {
        logger.detachAppender(appender)
        appender.stop()
    }
}

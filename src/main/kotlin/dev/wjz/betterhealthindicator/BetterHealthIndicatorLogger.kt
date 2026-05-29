package dev.wjz.betterhealthindicator

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object BetterHealthIndicatorLogger {
    private const val LOGGER_NAME = "better_health_indicator"

    val logger: Logger = LoggerFactory.getLogger(LOGGER_NAME)

    fun debug(message: String, vararg arguments: Any?) {
        logger.debug(message, *arguments)
    }

    fun info(message: String, vararg arguments: Any?) {
        logger.info(message, *arguments)
    }

    fun warn(message: String, vararg arguments: Any?) {
        logger.warn(message, *arguments)
    }

    fun error(message: String, vararg arguments: Any?) {
        logger.error(message, *arguments)
    }

    fun error(message: String, throwable: Throwable) {
        logger.error(message, throwable)
    }
}

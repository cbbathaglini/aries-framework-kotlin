package org.hyperledger.ariesframework.util

object LogUtil {

    inline fun info(
        caller: Any,
        noinline message: () -> String,
    ) {
        log(caller, LogType.INFO, null, message)
    }

    inline fun warn(
        caller: Any,
        noinline message: () -> String,
    ) {
        log(caller, LogType.WARN, null, message)
    }

    inline fun error(
        caller: Any,
        throwable: Throwable? = null,
        noinline message: () -> String,
    ) {
        log(caller, LogType.ERROR, throwable, message)
    }

    @PublishedApi
    internal fun log(
        caller: Any,
        type: LogType,
        throwable: Throwable?,
        message: () -> String,
    ) {
        val clazz = caller::class.java
        val logger = org.slf4j.LoggerFactory.getLogger(clazz)

        if (!type.isEnabled(logger)) return

        val method = resolveCallerMethod(clazz)
        val prefix = "[${clazz.simpleName}][$method][sid=${Session.getId()}]"
        val finalMessage = "$prefix ${message()}"

        when (type) {
            LogType.INFO -> logger.info(finalMessage)
            LogType.WARN -> logger.warn(finalMessage)
            LogType.ERROR ->
                if (throwable != null) {
                    logger.error(finalMessage, throwable)
                } else logger.error(finalMessage)
        }
    }

    private fun resolveCallerMethod(clazz: Class<*>): String =
        Throwable().stackTrace
            .firstOrNull {
                it.className == clazz.name &&
                    it.methodName !in setOf("info", "warn", "error", "log")
            }
            ?.methodName ?: "unknown"
}

package org.hyperledger.ariesframework.util

enum class LogType {
    INFO {
        override fun isEnabled(logger: org.slf4j.Logger) =
            logger.isInfoEnabled
    },
    WARN {
        override fun isEnabled(logger: org.slf4j.Logger) =
            logger.isWarnEnabled
    },
    ERROR {
        override fun isEnabled(logger: org.slf4j.Logger) =
            logger.isErrorEnabled
    }, ;

    abstract fun isEnabled(logger: org.slf4j.Logger): Boolean
}

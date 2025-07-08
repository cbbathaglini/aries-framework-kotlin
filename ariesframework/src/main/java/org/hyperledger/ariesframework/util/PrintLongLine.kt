package org.hyperledger.ariesframework.util

import org.slf4j.LoggerFactory

class PrintLongLine {

    companion object{
        private val logger = LoggerFactory.getLogger(PrintLongLine::class.java)
        fun print(message: String) {
            val maxLogSize = 4000
            var i = 0
            while (i < message.length) {
                val end = (i + maxLogSize).coerceAtMost(message.length)
                logger.info(message.substring(i, end))
                i += maxLogSize
            }
        }
    }
}
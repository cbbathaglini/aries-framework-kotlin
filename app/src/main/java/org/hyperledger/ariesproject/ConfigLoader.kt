package org.hyperledger.ariesproject

import android.content.Context
import java.io.IOException
import java.util.Properties

object ConfigLoader {

    private const val FILE_NAME = "config.properties"

    fun loadProperties(context: Context): Properties {
        val properties = Properties()

        try {
            context.assets.open(FILE_NAME).use { inputStream ->
                properties.load(inputStream)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return properties
    }
}

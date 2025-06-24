package org.hyperledger.ariesframework.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer

class ConvertJson {
    companion object{
        inline fun <reified T> toJson(obj: T, extraModules: SerializersModule? = null): String {

            val combinedModule = SerializersModule {
                // registre seus tipos polimórficos aqui, se necessário
                polymorphic(Any::class) { }

                // inclui módulos externos, se fornecidos
                extraModules?.let { include(it) }
            }

            val json = Json {
                encodeDefaults = true
                prettyPrint = true
                serializersModule = combinedModule
            }

            return json.encodeToString(serializer(), obj)
        }
    }
}
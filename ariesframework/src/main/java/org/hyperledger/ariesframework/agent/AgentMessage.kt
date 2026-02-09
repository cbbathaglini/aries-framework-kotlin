package org.hyperledger.ariesframework.agent

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import org.hyperledger.ariesframework.agent.decorators.ThreadDecorator
import org.hyperledger.ariesframework.agent.decorators.TransportDecorator
import org.hyperledger.ariesframework.connection.models.didauth.didDocServiceModule
import org.hyperledger.ariesframework.util.LogUtil
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

@Serializable
open class AgentMessage(
    @SerialName("@id")
    var id: String,
    @SerialName("@type")
    var type: String,
    @SerialName("~thread")
    var thread: ThreadDecorator? = null,
    @SerialName("~transport")
    var transport: TransportDecorator? = null,
) {

    val threadId: String
        get() = thread?.threadId ?: id

    fun setThread(threadId: String, parentThreadId: String?) {
        this.thread = ThreadDecorator(
            threadId = threadId,
            parentThreadId = parentThreadId,
        )
    }

    open fun requestResponse(): Boolean {
        return true
    }

    fun toJsonString(): String {
        return MessageSerializer.encodeToString(this)
    }

    override fun toString(): String {
        return "AgentMessage(id='$id', type='$type', thread=$thread, transport=$transport)"
    }

    companion object {
        fun generateId(): String {
            return UUID.randomUUID().toString()
        }
    }
}

object MessageSerializer : JsonContentPolymorphicSerializer<AgentMessage>(AgentMessage::class) {
    private val serializers = mutableMapOf<String, KSerializer<AgentMessage>>()
    private val logger = LoggerFactory.getLogger(MessageSerializer::class.java)
    private val encoder = Json { serializersModule = didDocServiceModule }
    private val decoder = Json { ignoreUnknownKeys = true; serializersModule = didDocServiceModule }

//    @OptIn(InternalSerializationApi::class)
//    fun <T : AgentMessage> registerMessage(type: String, clazz: KClass<T>) {
//        serializers[type] = clazz.serializer() as KSerializer<AgentMessage>
//        logger.debug(type)
//        serializers[Dispatcher.replaceNewDidCommPrefixWithLegacyDidSov(type)] = clazz.serializer() as KSerializer<AgentMessage>
//    }

    @OptIn(ExperimentalSerializationApi::class)
    fun <T : AgentMessage> registerMessage(type: String, clazz: KClass<T>) {
        // KClass<T> -> KType
        val ktype = clazz.createType()
        val kser = serializer(ktype) // top-level overload aceita KType

        @Suppress("UNCHECKED_CAST")
        val asAgent = kser as KSerializer<AgentMessage>

        serializers[type] = asAgent
    }

    @OptIn(ExperimentalSerializationApi::class)
    override fun selectDeserializer(element: JsonElement): KSerializer<AgentMessage> {
        val type = element.jsonObject["@type"]?.jsonPrimitive?.content
        LogUtil.info(this) { "type of message: $type" }

        return serializers[type] ?: run {
            LogUtil.error(this) { "Message type $type is not registered for JSON decoding" }
            serializer<AgentMessage>() // <- em vez de AgentMessage.serializer()
        }
    }

    fun encodeToString(message: AgentMessage): String {
        if (!serializers.containsKey(message.type)) {
            LogUtil.error(this) { "Message type ${message.type} is not registered for JSON encoding" }
            return Json.encodeToString(message)
        }
        return encoder.encodeToString(serializers[message.type]!!, message)
    }

    fun decodeFromString(message: String): AgentMessage {
        return decoder.decodeFromString(this, message)
    }
}

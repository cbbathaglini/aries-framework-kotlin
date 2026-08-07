package org.hyperledger.ariesframework.util

import kotlin.reflect.KClass

interface PublicJwk

class Ed25519PublicJwk : PublicJwk
class P256PublicJwk : PublicJwk
class P384PublicJwk : PublicJwk
class P521PublicJwk : PublicJwk
class RsaPublicJwk : PublicJwk
class Secp256k1PublicJwk : PublicJwk
class X25519PublicJwk : PublicJwk

val SupportedPublicJwkClasses: List<KClass<out PublicJwk>> = listOf(
    Ed25519PublicJwk::class,
    P256PublicJwk::class,
    P384PublicJwk::class,
    P521PublicJwk::class,
    RsaPublicJwk::class,
    Secp256k1PublicJwk::class,
    X25519PublicJwk::class,
)

typealias SupportedPublicJwkClass = KClass<out PublicJwk>

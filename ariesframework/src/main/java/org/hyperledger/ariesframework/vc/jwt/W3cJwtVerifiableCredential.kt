package org.hyperledger.ariesframework.vc.jwt

// class W3cJwtVerifiableCredential(
//    val jwt: Jwt
// ) {
//    private val _credential: W3cCredential = getCredentialFromJwtPayload(jwt.payload)
//
//    companion object {
//        fun fromSerializedJwt(serializedJwt: String): W3cJwtVerifiableCredential {
//            val jwt = Jwt.fromSerializedJwt(serializedJwt)
//            return W3cJwtVerifiableCredential(jwt)
//        }
//    }
//
//    val credential: W3cCredential
//        get() = _credential
//
//    val serializedJwt: String
//        get() = jwt.serializedJwt
//
//    val context: List<JsonElement>
//        get() = credential.context
//
//    val id: String?
//        get() = credential.id
//
//    val type: List<String>
//        get() = credential.type
//
//    val issuer: W3cIssuerOrString
//        get() = credential.issuer
//
//    val issuanceDate: String
//        get() = credential.issuanceDate
//
//    val expirationDate: String?
//        get() = credential.expirationDate
//
//    val credentialSubject: List<W3cCredentialSubject>
//        get() = credential.credentialSubject
//
//    val credentialSchema: List<W3cCredentialSchema>?
//        get() = credential.credentialSchema
//
//    val credentialStatus: W3cCredentialStatus?
//        get() = credential.credentialStatus
//
//    val issuerId: String
//        get() = credential.issuerId
//
//    val credentialSchemaIds: List<String>
//        get() = credential.credentialSchemaIds
//
//    val credentialSubjectIds: List<String>
//        get() = credential.credentialSubjectIds
//
//    val contexts: List<JsonElement>
//        get() = credential.contexts
//
//    val claimFormat: ClaimFormat
//        get() = ClaimFormat.JwtVc
//
//    val encoded: String
//        get() = serializedJwt
//
//    val jsonCredential: W3cJsonCredential
//        get() = JsonTransformer.toJSON(credential) as W3cJsonCredential
// }

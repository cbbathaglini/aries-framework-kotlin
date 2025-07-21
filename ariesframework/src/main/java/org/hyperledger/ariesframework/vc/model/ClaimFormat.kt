package org.hyperledger.ariesframework.vc.model

enum class ClaimFormat(val value: String) {
    Jwt("jwt"),
    JwtVc("jwt_vc"),
    JwtVp("jwt_vp"),
    Ldp("ldp"),
    LdpVc("ldp_vc"),
    LdpVp("ldp_vp"),
    Di("di"),
    DiVc("di_vc"),
    DiVp("di_vp"),
    SdJwtVc("vc+sd-jwt"),
    MsoMdoc("mso_mdoc");

    companion object {
        fun fromValue(value: String): ClaimFormat? {
            return values().find { it.value == value }
        }
    }
}
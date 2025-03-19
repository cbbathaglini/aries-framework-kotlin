package org.hyperledger.ariesframework.credentials.v2

class CredentialsV2Constants {
    companion object {
        const val PROTOCOL_VERSION = "v2"
        const val PROPOSE_CREDENTIAL = "https://didcomm.org/issue-credential/2.0/propose-credential"
        const val PROBLEM_REPORT = "https://didcomm.org/issue-credential/2.0/problem-report"
        const val OFFER_CREDENTIAL = "https://didcomm.org/issue-credential/2.0/offer-credential"
        const val REQUEST_CREDENTIAL = "https://didcomm.org/issue-credential/2.0/request-credential"
        const val CREDENTIAL_PREVIEW = "https://didcomm.org/issue-credential/2.0/credential-preview"
        const val ACK = "https://didcomm.org/issue-credential/2.0/ack"
        const val ISSUE_CREDENTIAL = "https://didcomm.org/issue-credential/2.0/issue-credential"
    }
}
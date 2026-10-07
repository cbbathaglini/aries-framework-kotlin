# Aries Framework Kotlin

Aries Framework Kotlin is an Android framework for [Aries](https://github.com/hyperledger/aries) protocol.

## Features

Aries Framework Kotlin is an Android framework for the [Aries](https://github.com/hyperledger/aries) protocol, covering most of [AIP 1.0](https://github.com/hyperledger/aries-rfcs/tree/main/concepts/0302-aries-interop-profile#aries-interop-profile-version-10) and parts of AIP 2.0.

### Supported features

**Protocols / RFCs**
- ✅ ([RFC 0160](https://github.com/hyperledger/aries-rfcs/blob/master/features/0160-connection-protocol/README.md)) Connection Protocol (`connections/1.0`)
- ✅ ([RFC 0023](https://github.com/hyperledger/aries-rfcs/tree/main/features/0023-did-exchange)) DID Exchange Protocol (AIP 2.0) (`didexchange/1.1`)
- ✅ ([RFC 0434](https://github.com/hyperledger/aries-rfcs/blob/main/features/0434-outofband/README.md)) Out-of-Band Protocol (AIP 2.0) (`out-of-band/1.1`, handshake-reuse)
- ✅ ([RFC 0211](https://github.com/hyperledger/aries-rfcs/blob/master/features/0211-route-coordination/README.md)) Mediator Coordination Protocol (`coordinate-mediation/1.0`)
- ✅ ([RFC 0212](https://github.com/hyperledger/aries-rfcs/tree/main/features/0212-pickup) / pickup) Message Pickup (`messagepickup/1.0`, strategies `PickUpV1` and `Implicit`)
- ✅ ([RFC 0095](https://github.com/hyperledger/aries-rfcs/blob/master/features/0095-basic-message/README.md)) Basic Message Protocol (`basicmessage/1.0`)
- ✅ ([RFC 0036](https://github.com/hyperledger/aries-rfcs/blob/master/features/0036-issue-credential/README.md)) Issue Credential v1 (`issue-credential/1.0`)
- ✅ ([RFC 0453](https://github.com/hyperledger/aries-rfcs/blob/main/features/0453-issue-credential-v2/README.md)) Issue Credential v2 (AIP 2.0) (`issue-credential/2.0`)
- ✅ ([RFC 0037](https://github.com/hyperledger/aries-rfcs/tree/master/features/0037-present-proof/README.md)) Present Proof v1 (`present-proof/1.0`)
  - Does not implement the proposal path (Prover starts by receiving the request)
- ✅ ([RFC 0454](https://github.com/hyperledger/aries-rfcs/blob/main/features/0454-present-proof-v2/README.md)) Present Proof v2 (AIP 2.0) (`present-proof/2.0`)
- ✅ ([RFC 0035](https://github.com/hyperledger/aries-rfcs/blob/main/features/0035-report-problem/README.md)) Report Problem Protocol (`problem-report` for connections, issue-credential, present-proof, mediation)
- ✅ ([RFC 0183](https://github.com/hyperledger/aries-rfcs/blob/main/features/0183-revocation-notification/README.md) v1 & [RFC 0721](https://github.com/hyperledger/aries-rfcs/blob/main/features/0721-revocation-notification-v2/README.md) v2) Revocation Notification (`revocation_notification/1.0`, `.../2.0`)

**Credentials / Identity**
- ✅ AnonCreds — holder, issuer and verifier services, plus `AnonCreds`, `LegacyIndy`, `Ledger`, `Ethereum (ethr)`, `in-memory` and `did:webvh` registries
- ✅ W3C Verifiable Credentials — JSON-LD (Data Integrity) and JWT formats; Verifiable Presentation
- ✅ `did:webvh` — DID resolver and AnonCreds registry (enabled via `useDidWebvh`)

**Ledgers**
- ✅ Indy ledger service (genesis-based)
- ✅ Besu ledger service (`org.hyperledger:indy_besu_vdr`, enabled via `useBesuLedger`)
- ✅ Askar wallet with AnonCreds link secret and public DID

**Transports**
- ✅ HTTP
- ✅ WebSocket

### Not supported yet
- ❌ ([RFC 0056](https://github.com/hyperledger/aries-rfcs/blob/main/features/0056-service-decorator/README.md)) Service Decorator
- ❌ Message Pickup v2
- ⚠️ The **proposal/negotiation** receive path is connected for `issue-credential/2.0`; receiving proposals on `present-proof` (v1 and v2) and on `issue-credential/1.0` is not fully wired.

## Usage

App development using Aries Framework Kotlin is done in following steps:
1. Create an Agent instance.
2. Create a connection with another agent by receiving a connection invitation.
3. Receive credentials or proof requests by subscribing to event bus.

### Create an Agent instance

```kotlin
    val besuLedgerConfig = BesuLedgerConfig(
        configFile = "besu_config.json",
        multiledger = true,
    )

    val config = AgentConfig(
        walletKey = key,
        genesisPath = File(applicationContext.filesDir.absolutePath, genesisPath).absolutePath,
        mediatorConnectionsInvite = invitationUrl,
        mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
        label = "SampleApp",
        autoAcceptCredential = AutoAcceptCredential.Never,
        autoAcceptProof = AutoAcceptProof.Never,
        useLedgerService = true,
        useBesuLedger = true,
        useDidWebvh = false,
        besuLedgerConfig = besuLedgerConfig,
    )
    val agent = Agent(applicationContext, config)
    agent.initialize()
```

The `AgentConfig` supports additional fields to control which registry/ledger backend is used:

- `useLedgerService` — enables the ledger service (Indy). Default `false`.
- `useBesuLedger` — uses the Besu-based ledger service instead of (or alongside) Indy. Default `false`.
- `besuLedgerConfig` — a `BesuLedgerConfig(configFile, multiledger)` pointing to the Besu network JSON
  (see [Besu VDR dependency](#besu-vdr-dependency) for dependency setup).
- `useDidWebvh` — enables the `did:webvh` support (AnonCreds registry, DID resolver, emulator workaround). Default `false`.
- `cacheConfigFile` — asset file name used for ledger cache settings (defaults to `config.properties`).
- `ignoreRevocationCheck` — skips revocation checks when creating a presentation. Default `false`. For testing.

To create an agent, first create a key to encrypt the wallet and save it in the [EncryptedSharedPreferences](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences).
```Kotlin
    val key = Agent.generateWalletKey()
```

A genesis file for the indy pool should be included as a resource in the app bundle and should be copied to the file system before initializing the agent.
```kotlin
    val genesisPath = "von.txn"
    val inputStream = applicationContext.assets.open(genesisPath)
    val file = File(applicationContext.filesDir.absolutePath, genesisPath)
    if (!file.exists()) {
        file.outputStream().use { inputStream.copyTo(it) }
    }
```

If you want to use a mediator, set the `mediatorConnectionsInvite` in the config.
`mediatorConnectionsInvite` is a url containing either a connection invitation or an out-of-band invitation.
`mediatorPickupStrategy` need to be `MediatorPickupStrategy.Implicit` to connect to an ACA-Py mediator.

You can use WebSocket transport without a mediator, but you will need a mediator if the counterparty agent only supports http transport.

### Receive an invitation

Create a connection by receiving a connection invitation.
```kotlin
    val (_, connection) = agent.oob.receiveInvitationFromUrl(url)
```

You will generally get the invitation url by QR code scanning.
Once the connection is created, it is stored in the wallet and your counterparty agent can send you a credential or a proof request using the connection at any time. The connection record contains keys to encrypt or decrypt messages exchanged through the connection.

### Receive credentials or proof requests

Subscribe to agent.eventBus to receive events from the agent and use `agent.credentials` or `agent.proofs` commands to handle the requests.

```kotlin
    private fun subscribeEvents() {
        val app = application as WalletApp
        app.agent.eventBus.subscribe<AgentEvents.CredentialEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                if (it.record.state == CredentialState.OfferReceived) {
                    getCredential(it.record.id)
                } else if (it.record.state == CredentialState.Done) {
                    showAlert("Credential received")
                }
            }
        }
        app.agent.eventBus.subscribe<AgentEvents.ProofEvent> {
            lifecycleScope.launch(Dispatchers.Main) {
                if (it.record.state == ProofState.RequestReceived) {
                    sendProof(it.record.id)
                } else if (it.record.state == ProofState.Done) {
                    showAlert("Proof done")
                }
            }
        }
    }

    private fun getCredential(id: String) {
        val app = application as WalletApp
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                app.agent.credentials.acceptOffer(
                    AcceptOfferOptions(credentialRecordId = id, autoAcceptCredential = AutoAcceptCredential.Always),
                )
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    showAlert("Failed to receive a credential.")
                }
            }
        }
    }

    private fun sendProof(id: String) {
        val app = application as WalletApp
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val retrievedCredentials = app.agent.proofs.getRequestedCredentialsForProofRequest(id)
                val requestedCredentials = app.agent.proofService.autoSelectCredentialsForProofRequest(retrievedCredentials)
                app.agent.proofs.acceptRequest(id, requestedCredentials)
            } catch (e: Exception) {
                lifecycleScope.launch(Dispatchers.Main) {
                    showAlert("Failed to present proof.")
                }
            }
        }
    }
```

If you set `autoAcceptCredential` and `autoAcceptProof` to `Always` in the config, it will be done automatically and you don't need to subscribe to the events and handle the requests.

Another way to handle those requests is to implement your own `MessageHandler` class and register it to the agent.
```kotlin
    val messageHandler = MyOfferCredentialHandler()
    agent.dispatcher.registerHandler(messageHandler)
```

For your information, Aries Framework Kotlin refers to [Aries Framework Swift](https://github.com/hyperledger/aries-framework-swift) a lot, so the class name and API are almost the same.

## Sample App

`app` directory contains an Android sample app that demonstrates how to use Aries Framework Kotlin. The app receives a connection invitation from a QR code or from a URL input and handles credential offers and proof requests.

### Run against a local did:webvh server

The sample agent is configured in
`app/src/main/java/org/hyperledger/ariesproject/WalletApp.kt` with:

```kotlin
useLedgerService = false,
useBesuLedger = false,
useDidWebvh = true,
```

1. Start a did:webvh server on your development computer. For example, follow the
   [DID WebVH Server setup guide](https://identity.foundation/didwebvh-server-py/getting-started/).
   From that server's `server` directory, after configuring its environment:

   ```bash
   uv sync
   uv run uvicorn app:app --host 0.0.0.0 --port 8000
   ```

   Its API documentation is at `http://localhost:8000/docs`. The server hosts DID
   logs and AnonCreds resources; run a separate DIDComm issuer/verifier agent to
   send credential offers and proof requests. Publish an issuer DID, schema and
   credential definition using the server's
   [AnonCreds guide](https://identity.foundation/didwebvh-server-py/guides/anoncreds/).
   Revocable credentials also need a revocation registry definition, status lists
   and a tails file accessible to the Android app.

2. Make the published URLs reachable from Android. On the standard Android
   emulator, `10.0.2.2` reaches the development computer; on a physical device,
   use the computer's LAN address and allow access through its firewall. Android's
   `localhost` points to Android itself. For normal DID resolution, expose the
   local server through an HTTPS hostname with a certificate trusted by Android,
   and use that hostname when creating the issuer DID. The app derives resource
   URLs from identifiers such as
   `did:webvh:<SCID>:<domain>:<namespace>:<alias>/resources/<resource-id>`;
   there is no separate server-base-URL setting in the app.

3. Create the app's local configuration if it does not already exist:

   ```bash
   cp -n app/src/main/assets/config.properties.example app/src/main/assets/config.properties
   ```

   For an HTTPS deployment, leave `emulator=false`. For the existing local HTTP
   resource fallback, set:

   ```properties
   emulator=true
   # Optional: invitation from your DIDComm mediator
   # invitation_url=<mediator-out-of-band-invitation-url>
   ```

   **Local HTTP fallback limits:** `emulator=true` is consulted only when the
   AnonCreds DID-to-HTTPS transformation throws. It then fetches resources from
   `http://<domain>:8000/<namespace>/<alias>/resources/<resource-id>`. It rewrites
   the domain to `10.0.2.2` only if it matches an IPv4 address on Android's own
   network interfaces; arbitrary `192.168.x.x` addresses are not automatically
   rewritten. It does not retry a failed HTTPS request over HTTP, change DID log
   resolution, or rewrite DIDComm endpoints or tails URLs. For a physical device,
   normally keep `emulator=false` and use reachable HTTPS URLs.

   Configure `invitation_url` when using an HTTP-only counterparty, since the
   wallet needs a mediator to receive subsequent messages. A counterparty that
   supports WebSocket return routing can be used without a mediator. Ensure all
   invitation, mediator and DIDComm endpoints are reachable from Android too.
   Keep `config.properties` untracked; it is already ignored by Git.

4. Configure the package credentials described in
   [Besu VDR dependency](#besu-vdr-dependency), open the project in Android Studio,
   sync Gradle, and run the `app` configuration on an emulator or device
   (Android 8.0 / API 26 or newer). Alternatively, with a device connected:

   ```bash
   ./gradlew :app:installDebug
   ```

   Launch the sample app. Rebuild/reinstall after changing the asset configuration.

5. Generate a connection invitation with your issuer/verifier agent, then scan
   its QR code or paste its URL into the sample app's home screen. Send an
   Issue Credential 2.0 AnonCreds offer using the published did:webvh credential
   definition. Open the incoming notification and accept the offer. Send a
   Present Proof 2.0 request from the verifier, then review and accept it in the
   app; check the verification result on the verifier agent.

If resource fetching fails, check the exact resource URL in Android Studio's
Logcat and request it from the device's browser. Connection failures usually
indicate an unreachable address/port; certificate errors require a trusted HTTPS
setup; HTTP 404 indicates a missing namespace, alias or resource. For revocation
failures, also check the status-list links and tails URL.

The `app/src/main/assets` directory also contains `von.txn` and
`bcovrin-genesis.txn` for Indy configurations. `WalletApp.kt` copies `von.txn`,
but the did:webvh configuration above does not connect to an Indy or Besu ledger.

## Besu VDR dependency

The framework uses the published Android/Kotlin dependency from
[Aries UniFFI Wrappers](https://github.com/LF-Decentralized-Trust-labs/aries-uniffi-wrappers):

```groovy
implementation("org.hyperledger:indy_besu_vdr:0.3.1.3")
```

`settings.gradle` already configures the GitHub Packages repository. Configure
`githubUsername` and `githubToken` in your untracked `local.properties`, or use
`GITHUB_ACTOR` and `GITHUB_TOKEN` when `local.properties` is absent. The token must
have access to read the wrapper packages. Do not commit credentials.

Gradle resolves the Android variant with its generated Kotlin bindings and native
libraries; compiling Rust or copying bindings and `.so` files manually is no longer
required. Kotlin imports use `indy_besu_vdr.*` instead of `uniffi.indy_besu_vdr.*`.
Applications importing VDR types exposed by the framework should declare the same
VDR dependency explicitly.

Version `0.3.1.3` fixes the native library name mismatch. No library-name override
or call to `BesuVdr.configureNativeLibrary()` is needed.

Keep the Besu network configuration and contract ABI assets used by
`BesuLedgerConfig`. The dependency change does not replace these application assets.

## Contributing


## License

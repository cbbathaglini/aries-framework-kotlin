# Aries Framework Kotlin — módulo `ariesframework`

## Ledger Besu Wrapper (besu)

Para rodar com o **ledger besu wrapper** (necessário para usar a rede Besu), é preciso
**compilar** a biblioteca nativa [`vdr/uniffi` do projeto `indy-besu`](https://github.com/hyperledger-indy/indy-besu/tree/main/vdr/uniffi)
para a arquitetura correspondente ao dispositivo/emulador Android.

Ou seja: para o Kotlin/Android **não** se usa `cargo run` (que roda no host). Compila-se a
liberia nativa com `cross` e copia-se o `.so` gerado para o módulo `ledger-besu-wrapper`.

### 1. Instalar `cross` (se ainda não estiver disponível)

```sh
cargo install cross --git https://github.com/cross-rs/cross
```

### 2. Comandos para compilar por arquitetura

Execute na raiz de `vdr/uniffi` (ou onde estiver o crate indy-besu):

```sh
# x86_64 Android (emulador 64-bit)
cross build --release --target x86_64-linux-android

# arm64-v8a Android (dispositivos 64-bit)
cross build --release --target aarch64-linux-android

# armeabi-v7a Android (dispositivos 32-bit)
cross build --release --target armv7-linux-androideabi

# x86 Android (emulador 32-bit)
cross build --release --target i686-linux-android
```

### 3. Copiar o artefato gerado

Após `cross build`, copie o `.so` de `vdr/uniffi/target/<target>/release/libindy_besu_vdr_uniffi.so`
para o módulo `ledger-besu-wrapper`, usando o nome de ABI Android correspondente:

| Target (`cross build --target`) | ABI Android (`src/main/jniLibs/<abi>`) |
|--------------------------------|----------------------------------------|
| `x86_64-linux-android`         | `x86_64`                               |
| `aarch64-linux-android`        | `arm64-v8a`                            |
| `armv7-linux-androideabi`      | `armeabi-v7a`                          |
| `i686-linux-android`           | `x86`                                  |

Exemplo:

```sh
# dentro da raiz de vdr/uniffi
cp target/aarch64-linux-android/release/libindy_besu_vdr_uniffi.so \
   <repo>/ledger-besu-wrapper/src/main/jniLibs/arm64-v8a/libindy_besu_vdr_uniffi.so
```

O wrapper já está configurado para carregar as libs nativas de
`ledger-besu-wrapper/src/main/jniLibs` (ver `sourceSets.main.jniLibs` do `build.gradle`).

### 4. Gerar os bindings Kotlin (`indy_besu_vdr.kt`)

Para gerar o arquivo de bindings Kotlin `indy_besu_vdr.kt` (necessário para o módulo
`ledger-besu-wrapper`), é preciso rodar o gerador `uniffi-bindgen` apontando para a
biblioteca nativa compilada no host (`libindy_besu_vdr_uniffi.dylib` em macOS, ou
`libindy_besu_vdr_uniffi.so` em Linux), na raiz de `vdr/uniffi`:

```sh
cargo run --bin uniffi-bindgen generate \
  --library target/release/libindy_besu_vdr_uniffi.dylib \
  --language kotlin \
  --out-dir out
```

> Para outras linguagens, troque `--language kotlin` por `--language <python|swift>`.
> A instrução completa também está no `README.md` do projeto `indy-besu`.


# Building and auditing Discofi (Resymbian fork)

This fork builds entirely from source in GitHub Actions. No release artifact is
built on a developer machine, and every third-party binary used by the build is
pinned by version and SHA-256.

## What CI does

`.github/workflows/build.yml` runs on every push, pull request, tag and manual
dispatch:

1. Checks out the exact commit with `actions/checkout`, pinned by commit SHA.
2. Installs Node.js with `actions/setup-node`, pinned by commit SHA.
3. Runs `sdk/fetch-tools.sh`, which downloads:
   - Temurin JDK **8u514-b01** and verifies SHA-256
     `27eda854481d20806d918b30cdd417c6bed4fd15882e24f3595dc8ae7fb5d095`
   - ProGuard **6.2.2** from Maven Central and verifies SHA-256
     `7587a3a415d6aea1946f597f4babe710a245f085bac4dc5e7cda653acfc39b03`
4. Builds all four MIDlet targets: `discofi`, `discofi_samsung`,
   `discofi_lite`, `discofi_debug`.
5. Runs `sdk/package.py`, which:
   - verifies the hashes of every committed `sdk/lib/*.jar` and emoji asset
     against `sdk/lib.sha256` and `res/emoji.sha256`;
   - normalizes JAR entry order and timestamps (fixed 1980-01-01) and forces
     the manifest to CRLF, as required by the JAR format and accepted by
     strict J2ME JVMs;
   - checks each JAR has a valid manifest, a Java 1.2 class version (46), the
     expected emoji assets (not in `lite`), no debug-only classes in releases,
     and stays within its size budget;
   - writes a JAD for every JAR and `bin/SHA256SUMS`.
6. Repeats a clean build and fails unless `bin/SHA256SUMS` is byte-for-byte
   identical. The fork is reproducible with the pinned toolchain.
7. Runs `sdk/test.sh`: a headless MicroEmulator MIDlet that starts the app,
   checks emoji lookup/rendering, reactions, the picker and the compose draft,
   then calls `destroyApp()` and requires `notifyDestroyed()` to end the
   process. A startup error or a stuck background thread fails CI.
8. Generates a signed SLSA build provenance attestation for every JAR, JAD and
   `SHA256SUMS` with `actions/attest-build-provenance`.
9. Uploads the artifacts. A `v*` tag also publishes them as a GitHub release.

## Verify a downloaded artifact

```bash
sha256sum -c SHA256SUMS
gh attestation verify discofi.jar --repo resymbian/Discofi
```

The second command checks that the JAR was produced by this repository's
workflow from the recorded commit.

## Building locally

```bash
./sdk/fetch-tools.sh
./build.sh
python3 sdk/package.py
```

The toolchain is only used locally for development. Do not publish artifacts
built outside CI; the provenance attestation covers only CI builds.

## Binaries that are not built from source

The following are committed to the repository and are part of the MIDP/J2ME
platform API, not the application logic:

- `sdk/lib/*.jar` — CLDC/MIDP and optional API stubs, checksummed in
  `sdk/lib.sha256`.
- `res/emoji/*.png`, `res/emoji/emoji.dat` — Twemoji artwork, attributed in
  `res/emoji/NOTICE.txt` and checksummed in `res/emoji.sha256`.

ProGuard and the JDK are downloaded during CI and never committed.

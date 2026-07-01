# Milestone 6 — Digital signatures & certificate chains

Every APK is signed. This milestone extracts the signing certificate(s) during
the same read-only analysis and shows their fingerprints — a tour of how Android
verifies package authenticity.

---

## 1. Why APKs are signed

Android has no central gatekeeper for *who* an app came from; instead it uses
**signature-based identity**. The signing certificate:

- proves successive updates come from the same author (the OS refuses an update
  signed by a different key),
- backs `signature`-level permission sharing between apps by the same signer,
- is the identity Play App Signing and `apksigner` report as a fingerprint.

## 2. Getting the certificates

We ask the platform parser for signing data alongside everything else, with an
API-gated flag:

```kotlin
val signingFlag = if (SDK_INT >= P) GET_SIGNING_CERTIFICATES else GET_SIGNATURES
getPackageArchiveInfo(path, GET_PERMISSIONS or signingFlag)
```

- **API 28+**: `packageInfo.signingInfo` exposes `apkContentsSigners` and a
  `hasMultipleSigners` flag (and rotation history via `signingCertificateHistory`).
- **Below 28**: the older `packageInfo.signatures` array (deprecated but still
  works for older devices).

Each `Signature` holds the **DER bytes of an X.509 certificate**. We parse it
with the JCA:

```kotlin
val cert = CertificateFactory.getInstance("X.509")
    .generateCertificate(ByteArrayInputStream(sig.toByteArray())) as X509Certificate
```

and read subject, issuer, serial, validity window, signature algorithm, and
public-key algorithm.

## 3. Fingerprints

A certificate fingerprint is just a cryptographic digest of the cert's DER
bytes:

```kotlin
MessageDigest.getInstance("SHA-256").digest(cert.encoded)
    .joinToString(":") { "%02X".format(it) }
```

We compute SHA-256 and SHA-1. These are exactly the values `apksigner verify
--print-certs` and the Play Console show, so you can cross-check an app's
identity against a known-good fingerprint.

## 4. Signing schemes (v1/v2/v3) — what we detect, and why the rest is hard

- **v1 (JAR signing)** lives *inside* the ZIP as `META-INF/MANIFEST.MF`, `*.SF`,
  and `*.RSA/.DSA/.EC`. We detect it directly from the ZIP entry list.
- **v2/v3 (APK Signature Scheme)** live in the **APK Signing Block**, a region
  inserted between the ZIP entries and the central directory (found via a magic
  string `APK Sig Block 42`). Parsing it means walking the ZIP end-of-central-
  directory record back to the block — a deeper exercise left as a stretch. The
  certificates we show come from the platform's own verified parse, which already
  honors v2/v3, so the *identity* is correct regardless of scheme.

The result surfaces as a **Signature** tab: scheme chips, then a card per
certificate with subject/issuer, validity dates, algorithms, and the monospace
fingerprints. All read-only — we only read the certs the APK ships with.

---

## Try it / observe it

- Open any APK or installed app → **Signature** tab → subject/issuer, validity,
  and SHA-256/SHA-1 fingerprints.
- Most app certs are **self-signed** (issuer == subject) — the UI says so.
- Compare a system app's fingerprint across two devices from the same vendor —
  same signing identity.

> Build note: `:core:model` compile-verified here; `:data:apk` and `:app` need
> the Android SDK and are validated by review plus your local build.

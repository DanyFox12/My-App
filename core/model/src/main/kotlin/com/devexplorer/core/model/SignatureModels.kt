package com.devexplorer.core.model

import kotlinx.serialization.Serializable

/** One X.509 certificate from an APK's signing chain. */
@Serializable
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val notBefore: Long,
    val notAfter: Long,
    val sha256: String,
    val sha1: String,
    val signatureAlgorithm: String,
    val publicKeyAlgorithm: String,
) {
    /** True when issuer == subject (typical for app-signing certs). */
    val isSelfSigned: Boolean get() = issuer == subject
}

/** Signing information extracted from an APK. */
@Serializable
data class SigningInfo(
    val certificates: List<CertificateInfo>,
    val hasMultipleSigners: Boolean,
    /** v1 (JAR) signing detected from META-INF signature files present in the ZIP. */
    val schemeV1: Boolean,
)

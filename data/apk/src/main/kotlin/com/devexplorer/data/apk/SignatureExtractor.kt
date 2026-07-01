package com.devexplorer.data.apk

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Build
import com.devexplorer.core.model.CertificateInfo
import com.devexplorer.core.model.SigningInfo
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Turns a parsed [PackageInfo]'s signing data into our framework-free
 * [SigningInfo]. Read-only: we only *read* the certificates the APK ships with.
 *
 * A [Signature] here holds the DER bytes of an X.509 certificate. We parse it
 * with [CertificateFactory] and compute the SHA-256 / SHA-1 fingerprints — the
 * same digests tools like `apksigner` and Play App Signing show.
 */
internal object SignatureExtractor {

    fun extract(info: PackageInfo, hasV1SignatureFiles: Boolean): SigningInfo? {
        val signatures = info.currentSignatures() ?: return null
        if (signatures.isEmpty()) return null

        val certs = signatures.mapNotNull { it.toCertificateInfo() }
        if (certs.isEmpty()) return null

        val multipleSigners =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.hasMultipleSigners() == true
            } else {
                signatures.size > 1
            }

        return SigningInfo(
            certificates = certs,
            hasMultipleSigners = multipleSigners,
            schemeV1 = hasV1SignatureFiles,
        )
    }

    private fun PackageInfo.currentSignatures(): Array<Signature>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            signatures
        }

    private fun Signature.toCertificateInfo(): CertificateInfo? = runCatching {
        val factory = CertificateFactory.getInstance("X.509")
        val cert = factory.generateCertificate(ByteArrayInputStream(toByteArray())) as X509Certificate
        val der = cert.encoded
        CertificateInfo(
            subject = cert.subjectX500Principal.name,
            issuer = cert.issuerX500Principal.name,
            serialNumber = cert.serialNumber.toString(16),
            notBefore = cert.notBefore.time,
            notAfter = cert.notAfter.time,
            sha256 = der.fingerprint("SHA-256"),
            sha1 = der.fingerprint("SHA-1"),
            signatureAlgorithm = cert.sigAlgName,
            publicKeyAlgorithm = cert.publicKey.algorithm,
        )
    }.getOrNull()

    private fun ByteArray.fingerprint(algorithm: String): String =
        MessageDigest.getInstance(algorithm)
            .digest(this)
            .joinToString(":") { "%02X".format(it) }
}

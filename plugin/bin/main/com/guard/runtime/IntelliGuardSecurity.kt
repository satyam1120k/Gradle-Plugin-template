package com.guard.runtime

import java.security.MessageDigest

@Suppress("unused", "FunctionName")
object IntelliGuardSecurity {

    @JvmStatic
    fun isTampered(): Boolean {
        try {
            // 1. Anti-Debugging Check via Reflection on android.os.Debug
            val debugClass = Class.forName("android.os.Debug")
            val isDebuggerConnected = debugClass.getMethod("isDebuggerConnected").invoke(null) as? Boolean ?: false
            val waitingForDebugger = debugClass.getMethod("waitingForDebugger").invoke(null) as? Boolean ?: false
            if (isDebuggerConnected || waitingForDebugger) {
                return true
            }

            // 2. Dynamic Context Resolution via Reflection on android.app.ActivityThread
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentAppMethod = activityThreadClass.getMethod("currentApplication")
            val context = currentAppMethod.invoke(null) ?: return true

            val packageManager = context.javaClass.getMethod("getPackageManager").invoke(context)
            val packageName = context.javaClass.getMethod("getPackageName").invoke(context) as? String ?: return true

            // 3. Anti-Tampering: Check Signature Certificate Fingerprint via Reflection
            val buildVersion = Class.forName("android.os.Build\$VERSION")
            val sdkInt = buildVersion.getField("SDK_INT").getInt(null)

            val packageInfoClass = Class.forName("android.content.pm.PackageInfo")
            val flags = if (sdkInt >= 28) { // Build.VERSION_CODES.P
                Class.forName("android.content.pm.PackageManager").getField("GET_SIGNING_CERTIFICATES").getInt(null)
            } else {
                Class.forName("android.content.pm.PackageManager").getField("GET_SIGNATURES").getInt(null)
            }

            val packageInfo = packageManager?.javaClass?.getMethod("getPackageInfo", String::class.java, Int::class.javaPrimitiveType)
                ?.invoke(packageManager, packageName, flags) ?: return true

            val signatures = if (sdkInt >= 28) {
                val signingInfoField = packageInfoClass.getField("signingInfo")
                val signingInfo = signingInfoField.get(packageInfo)
                if (signingInfo != null) {
                    val hasMultipleSigners = signingInfo.javaClass.getMethod("hasMultipleSigners").invoke(signingInfo) as? Boolean ?: false
                    if (hasMultipleSigners) {
                        signingInfo.javaClass.getMethod("getApkContentsSigners").invoke(signingInfo) as? Array<*>
                    } else {
                        signingInfo.javaClass.getMethod("getSigningCertificateHistory").invoke(signingInfo) as? Array<*>
                    }
                } else null
            } else {
                val signaturesField = packageInfoClass.getField("signatures")
                signaturesField.get(packageInfo) as? Array<*>
            }

            if (signatures.isNullOrEmpty()) {
                return true
            }

            // Compute SHA-256 hash of signature certificates
            for (sig in signatures) {
                if (sig == null) continue
                val byteArray = sig.javaClass.getMethod("toByteArray").invoke(sig) as? ByteArray ?: continue
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(byteArray)
                val signatureHash = digest.digest().joinToString("") { "%02x".format(it) }

                // TODO: Replace with your official release keystore SHA-256 hash string.
                // If an attacker repackages or re-signs the APK, the hash mismatch triggers tampering detection.
                val expectedReleaseHash = "YOUR_PRODUCTION_KEYSTORE_SHA256_HASH"
                if (expectedReleaseHash != "YOUR_PRODUCTION_KEYSTORE_SHA256_HASH" && signatureHash != expectedReleaseHash) {
                    return true
                }
            }

        } catch (ignored: Throwable) {
            // If reflection or package verification is intercepted / hooked, flag as tampered
            return true
        }

        return false
    }
}
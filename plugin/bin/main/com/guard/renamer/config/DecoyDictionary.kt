package com.guard.renamer.config

object DecoyDictionary {

    val DECOY_CLASS_NAMES = listOf(
        "com/google/android/gms/analytics/DataTracker",
        "com/google/firebase/messaging/SyncService",
        "com/android/internal/policy/PhoneWindowHelper",
        "com/squareup/okhttp/internal/DiskLruCacheManager",
        "io/reactivex/internal/observers/LambdaObserver",
        "com/facebook/soloader/NativeLibraryLoader",
        "com/adjust/sdk/ActivityHandler",
        "org/apache/commons/codec/binary/Base64Encoder"
    )

    val DECOY_METHOD_NAMES = listOf(
        "verifySecurityToken",
        "fetchAnalyticsMetadata",
        "decompressPayload",
        "encryptSessionKey",
        "validateChecksum",
        "executeBackgroundSync",
        "flushCacheBuffer",
        "parseServerConfig",
        "onNetworkStateChanged",
        "dispatchTelemetryEvent",
        "rotateEncryptionKeys",
        "sanitizeUserInput"
    )

    val DECOY_FIELD_NAMES = listOf(
        "authToken",
        "isEncrypted",
        "sessionTimeout",
        "apiSecretKey",
        "retryCount",
        "lastSyncTimestamp",
        "deviceFingerprint",
        "cacheBufferSize",
        "isTelemetryEnabled",
        "signatureHash"
    )
}
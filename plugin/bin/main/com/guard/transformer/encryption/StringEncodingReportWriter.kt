package com.guard.transformer.encryption

import java.io.File

data class EncodedStringLog(
    val className: String,
    val methodName: String,
    val originalValue: String,
    val encodedValue: String
)

object StringEncodingReportWriter {

    fun writeReport(
        reportFile: File,
        targetedMethodCount: Int,
        logs: List<EncodedStringLog>
    ) {
        reportFile.parentFile?.mkdirs()
        reportFile.printWriter().use { out ->
            out.println("==================================================")
            out.println(" IntelliGuard Gitleaks-Driven Secret Report       ")
            out.println("==================================================")
            out.println("Targeted Methods             : $targetedMethodCount")
            out.println("Gitleaks Rules Active        : ${SecretPatternDatabase.config?.rules?.size ?: 0}")
            out.println("Encoded Secret Literals      : ${logs.size}")
            out.println("==================================================")
            out.println()

            if (logs.isEmpty()) {
                out.println("No sensitive secrets matched gitleaks.toml rules in targeted methods.")
            } else {
                out.println("Discovered & Encrypted Secrets:")
                out.println("--------------------------------------------------")
                logs.forEachIndexed { index, log ->
                    out.println("[${index + 1}] Class  : ${log.className}")
                    out.println("    Method : ${log.methodName}")
                    out.println("    Plain  : \"${log.originalValue}\"")
                    out.println("    Encoded: \"${log.encodedValue}\"")
                    out.println()
                }
            }
        }
    }
}
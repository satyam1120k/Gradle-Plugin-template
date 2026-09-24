package com.guard.transformer.antiTempering

import java.io.File

object AntiTamperingReportWriter {

    fun writeReport(
        reportFile: File,
        eligibleMethods: Int,
        protectedMethods: Int,
        logs: List<String>
    ) {
        // Ensure the IntelliGuard output directory exists before writing
        reportFile.parentFile?.mkdirs()

        reportFile.printWriter().use { out ->
            out.println("==================================================")
            out.println(" IntelliGuard Anti-Tampering Protection Report      ")
            out.println("==================================================")
            out.println("Eligible Critical Methods (Anti-Tampering) : $eligibleMethods")
            out.println("Methods Actively Protected                 : $protectedMethods")
            out.println("==================================================")
            out.println()

            if (logs.isEmpty()) {
                out.println("No matching Critical methods found for anti-tampering injection.")
            } else {
                out.println("Detailed Injection Log:")
                out.println("--------------------------------------------------")
                logs.forEach { log -> out.println("- $log") }
            }
        }
    }
}
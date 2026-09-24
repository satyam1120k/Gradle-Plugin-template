package com.guard.transformer.deadCode

import java.io.File

object DeadCodeReportWriter {

    fun writeReport(
        reportFile: File,
        eligibleMethods: Int,
        transformedMethods: Int,
        logs: List<DeadCodeInjectionLog>
    ) {
        reportFile.parentFile?.mkdirs()
        reportFile.printWriter().use { out ->
            out.println("==================================================")
            out.println(" IntelliGuard Dead Code Insertion Report          ")
            out.println("==================================================")
            out.println("Eligible Target Methods (High/Critical) : $eligibleMethods")
            out.println("Methods Actively Protected              : $transformedMethods")
            out.println("Total Dead Code Blocks Injected         : ${logs.size}")
            out.println("==================================================")
            out.println()

            if (logs.isEmpty()) {
                out.println("No matching High/Critical methods found for dead code injection.")
            } else {
                out.println("Detailed Transformation Log (Targeted Methods & Patterns):")
                out.println("--------------------------------------------------")
                logs.forEachIndexed { index, log ->
                    out.println("[${index + 1}] Class  : ${log.className}")
                    out.println("    Method : ${log.methodName}")
                    out.println("    Pattern: ${log.patternUsed}")
                    out.println()
                }
            }
        }
    }
}
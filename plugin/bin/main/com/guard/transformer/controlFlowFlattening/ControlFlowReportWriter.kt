package com.guard.transformer.controlFlowFlattening

import java.io.File

object ControlFlowReportWriter {

    fun writeReport(
        reportFile: File,
        eligibleMethods: Int,
        transformedMethods: Int,
        logs: List<String>
    ) {
        reportFile.printWriter().use { out ->
            out.println("==================================================")
            out.println(" IntelliGuard Control Flow Flattening Report      ")
            out.println("==================================================")
            out.println("Eligible Target Methods (Critical) : $eligibleMethods")
            out.println("Methods Actively Flattened         : $transformedMethods")
            out.println("==================================================")
            out.println()

            if (logs.isEmpty()) {
                out.println("No methods met the structural requirements for flattening.")
            } else {
                out.println("Detailed Transformation Log:")
                out.println("--------------------------------------------------")
                logs.forEach { log -> out.println("- $log") }
            }
        }
    }
}
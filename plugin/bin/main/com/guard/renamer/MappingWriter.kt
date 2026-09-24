package com.guard.renamer

import java.io.File

object MappingWriter {

    fun writeReport(outputDir: File, renameMap: Map<String, String>) {
        val mappingFile = File(outputDir, "renaming_mapping.txt")
        mappingFile.parentFile?.mkdirs()
        mappingFile.printWriter().use { out ->
            out.println("======================================")
            out.println(" IntelliGuard Native Full Obfuscation Map ")
            out.println("======================================")
            out.println()
            if (renameMap.isEmpty()) {
                out.println("No identifiers matched renaming criteria.")
            } else {
                renameMap.forEach { (original, obfuscated) ->
                    if (original != obfuscated) {
                        out.println("$original  ==>  $obfuscated")
                    }
                }
            }
        }
    }
}
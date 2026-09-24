package com.guard.analyzer

import com.guard.model.RichGraphDataset
import java.io.File

object ProGuardRuleGenerator {

    fun generateRules(dataset: RichGraphDataset, outputDir: File, projectRoot: File? = null): File {
        val rulesFile = File(outputDir, "generated_proguard_rules.pro")
        rulesFile.parentFile?.mkdirs()

        val collectedRules = mutableSetOf<String>()

        collectedRules.add("# ======================================")
        collectedRules.add("#  IntelliGuard Auto-Generated R8 Rules ")
        collectedRules.add("# ======================================")
        collectedRules.add("")
        collectedRules.add("-repackageclasses 'com.guard.obf'")
        collectedRules.add("-allowaccessmodification")
        collectedRules.add("-ignorewarnings")
        collectedRules.add("")
        collectedRules.add("-dontwarn sun.misc.**")
        collectedRules.add("-dontwarn retrofit2.**")
        collectedRules.add("-dontwarn okhttp3.**")
        collectedRules.add("-dontwarn kotlinx.**")
        collectedRules.add("-dontwarn com.google.**")
        collectedRules.add("")
        collectedRules.add("# Preserve Android Framework Entry Points & Lifecycle Hooks")
        collectedRules.add("-keep public class * extends android.app.Application")
        collectedRules.add("-keep public class * extends android.app.Activity")
        collectedRules.add("-keep public class * extends android.app.Fragment")
        collectedRules.add("-keep public class * extends androidx.fragment.app.Fragment")
        collectedRules.add("-keep public class * extends androidx.lifecycle.ViewModel")
        collectedRules.add("")

        // Dynamically keep high-risk / sensitive classes discovered by graph analysis
        dataset.nodes.forEach { node ->
            if (node.riskCategory == "Critical" || node.riskCategory == "High" || node.sensitiveDataFlow > 0) {
                // Use dot notation (node.className), NOT slash notation (replace('.', '/'))
                val className = node.className
                collectedRules.add("-keep class $className { *; }")
            }
        }

        // Automatically ingest R8's missing_rules.txt if generated in previous build passes
        if (projectRoot != null) {
            val missingRulesFile = File(projectRoot, "app/build/outputs/mapping/debug/missing_rules.txt")
            if (missingRulesFile.exists()) {
                collectedRules.add("")
                collectedRules.add("# Auto-imported missing rules from R8 analysis")
                missingRulesFile.readLines().forEach { line ->
                    if (line.isNotBlank()) {
                        collectedRules.add(line)
                    }
                }
            }
        }

        rulesFile.writeText(collectedRules.joinToString("\n"))
        return rulesFile
    }
}
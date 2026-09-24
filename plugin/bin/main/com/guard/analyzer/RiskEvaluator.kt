package com.guard.analyzer

import com.guard.model.FeatureVector

data class ObfuscationPlan(
    val category: String,
    val score: Int,
    val actions: List<String>
)

class RiskEvaluator {

    fun evaluate(features: FeatureVector): ObfuscationPlan {
        var score = 0

        score += features.cryptoApiCount * 3
        score += features.sensitiveDataFlow * 5
        score += features.reflectionUsed * 3
        score += features.networkApiCount * 1
        score += features.fileApiCount * 1
        score += features.loopCount * 1
        score += features.branchCount * 1
        score += features.permissionScore
        score += (features.pageRank).toInt()

        return when {
            score > 15 -> ObfuscationPlan(
                category = "Critical",
                score = score,
                actions = listOf(
                    "Identifier Renaming", "Code Shrinking", "peephole Optimization", "Package Class Renaming",
                    "Control Flow Obfuscation", "Reflection", "Junk Code Insertion", "Loop Transformation",
                    "String Encryption", "Control Flow Flattening", "anti-debugging", "anti-tempering"
                )
            )
            score > 10 -> ObfuscationPlan(
                category = "High",
                score = score,
                actions = listOf(
                    "Identifier Renaming", "Code Shrinking", "peephole Optimization", "Package Class Renaming",
                    "Control Flow Obfuscation", "Reflection", "Junk Code Insertion", "Loop Transformation",
                    "XOR Encryption"
                )
            )
            score > 5 -> ObfuscationPlan(
                category = "Medium",
                score = score,
                actions = listOf(
                    "Identifier Renaming", "Code Shrinking", "peephole Optimization", "Package Class Renaming",
                    "Control Flow Obfuscation", "String Encoding", "Reflection"
                )
            )
            else -> ObfuscationPlan(
                category = "Low",
                score = score,
                actions = listOf("Identifier Renaming", "Code Shrinking", "peephole Optimization")
            )
        }
    }
}
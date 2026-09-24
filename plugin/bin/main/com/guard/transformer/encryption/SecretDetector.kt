package com.guard.transformer.encryption

import java.util.regex.Pattern
import kotlin.math.log2

object SecretDetector {

    // High-confidence secret prefixes that skip keyword filtering
    private val HIGH_CONFIDENCE_PREFIXES = listOf(
        Pattern.compile("^AKIA[0-9A-Z]{16}$"),        // AWS Access Key ID
        Pattern.compile("^ASIA[0-9A-Z]{16}$"),        // AWS Temporary Key ID
        Pattern.compile("^ghp_[a-zA-Z0-9]{36,255}$"), // GitHub Personal Access Token
        Pattern.compile("^glpat-[a-zA-Z0-9\\-_]{20,255}$"), // GitLab Personal Access Token
        Pattern.compile("^sk_live_[0-9a-zA-Z]{24,}$") // Stripe Live Key
    )

    private val SCHEMA_ALLOWLIST = listOf(
        "http://schemas.android.com",
        "http://www.w3.org/",
        "http://xmlns.jcp.org/",
        "https://schema.org"
    )

    fun isSensitiveSecret(value: String): Boolean {
        val trimmed = value.trim()

        if (trimmed.length < 3) return false
        if (isSqlQuery(trimmed)) return false

        // 1. Direct Prefix Check (Catches AWS, GitHub, Stripe tokens instantly without keyword/entropy checks)
        if (HIGH_CONFIDENCE_PREFIXES.any { pattern -> pattern.matcher(trimmed).matches() }) {
            return true
        }

        // 2. Direct HTTP/HTTPS Web Endpoints Check
        if ((trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
            SCHEMA_ALLOWLIST.none { trimmed.startsWith(it, ignoreCase = true) }) {
            return true
        }

        // 3. Exclude Android Compose & UI Metadata
        if (trimmed.startsWith("C(") || (trimmed.contains("@") && trimmed.contains("L") && trimmed.contains("#"))) {
            return false
        }

        val gitleaksConfig = SecretPatternDatabase.config ?: return false

        // Check Global Allowlist
        if (gitleaksConfig.allowlist.stopwords.contains(trimmed)) return false
        if (gitleaksConfig.allowlist.globalRegexes.any { it.matcher(trimmed).find() }) return false

        val lower = trimmed.lowercase()

        // Evaluate Gitleaks Rules
        for (rule in gitleaksConfig.rules) {
            val patternMatch = rule.regex?.matcher(trimmed)?.find() ?: false
            if (!patternMatch) continue

            // If regex matches, verify entropy if specified
            if (rule.entropy != null) {
                val stringEntropy = calculateShannonEntropy(trimmed)
                if (stringEntropy < rule.entropy) continue
            }

            return true
        }

        return false
    }

    private fun isSqlQuery(input: String): Boolean {
        val upper = input.uppercase()
        return upper.startsWith("PRAGMA ") ||
                upper.startsWith("CREATE TABLE") ||
                upper.startsWith("SELECT ") ||
                upper.startsWith("INSERT INTO")
    }

    private fun calculateShannonEntropy(input: String): Double {
        if (input.isEmpty()) return 0.0
        val frequencies = input.groupingBy { it }.eachCount()
        val length = input.length.toDouble()
        return frequencies.values.sumOf { count ->
            val p = count / length
            -p * log2(p)
        }
    }
}
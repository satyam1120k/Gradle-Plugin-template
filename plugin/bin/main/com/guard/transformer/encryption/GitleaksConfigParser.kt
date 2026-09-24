package com.guard.transformer.encryption

import java.io.InputStream
import java.util.regex.Pattern

data class RuleAllowlist(
    val description: String = "",
    val regexes: List<Pattern> = emptyList(),
    val stopwords: List<String> = emptyList()
)

data class GitleaksRule(
    val id: String,
    val description: String,
    val regex: Pattern?,
    val keywords: List<String>,
    val entropy: Double? = null,
    val allowlists: List<RuleAllowlist> = emptyList()
)

data class GitleaksAllowlist(
    val pathRegexes: List<Pattern>,
    val globalRegexes: List<Pattern>,
    val stopwords: List<String>
)

data class GitleaksConfig(
    val allowlist: GitleaksAllowlist,
    val rules: List<GitleaksRule>
)

object GitleaksConfigParser {

    fun parse(inputStream: InputStream): GitleaksConfig {
        val content = inputStream.bufferedReader().use { it.readText() }

        val allowlistPaths = mutableListOf<Pattern>()
        val allowlistRegexes = mutableListOf<Pattern>()
        val allowlistStopwords = mutableListOf<String>()
        val rules = mutableListOf<GitleaksRule>()

        // 1. Extract Global [allowlist]
        if (content.contains("[allowlist]")) {
            val globalSection = content.substringAfter("[allowlist]").substringBefore("[[rules]]")
            extractTomlList(globalSection, "paths").forEach { tryCompile(it)?.let { p -> allowlistPaths.add(p) } }
            extractTomlList(globalSection, "regexes").forEach { tryCompile(it)?.let { p -> allowlistRegexes.add(p) } }
            allowlistStopwords.addAll(extractTomlList(globalSection, "stopwords"))
        }

        // 2. Extract [[rules]] from gitleaks.toml
        val rawRuleBlocks = content.split("[[rules]]").drop(1)
        rawRuleBlocks.forEach { ruleBlock ->
            val id = extractTomlValue(ruleBlock, "id")
            val description = extractTomlValue(ruleBlock, "description")
            val rawRegex = extractTomlRawRegex(ruleBlock)
            val keywords = extractTomlList(ruleBlock, "keywords")
            val entropyStr = extractTomlValue(ruleBlock, "entropy")
            val entropy = entropyStr.toDoubleOrNull()

            val compiledPattern = tryCompile(rawRegex)

            // Extract Rule-Specific [[rules.allowlists]]
            val ruleAllowlists = mutableListOf<RuleAllowlist>()
            if (ruleBlock.contains("[[rules.allowlists]]")) {
                val allowlistSubBlocks = ruleBlock.split("[[rules.allowlists]]").drop(1)
                allowlistSubBlocks.forEach { subBlock ->
                    val allowlistDesc = extractTomlValue(subBlock, "description")
                    val subRegexes = extractTomlList(subBlock, "regexes").mapNotNull { tryCompile(it) }
                    val subStopwords = extractTomlList(subBlock, "stopwords")

                    if (subRegexes.isNotEmpty() || subStopwords.isNotEmpty()) {
                        ruleAllowlists.add(RuleAllowlist(allowlistDesc, subRegexes, subStopwords))
                    }
                }
            }

            if (id.isNotBlank() && (compiledPattern != null || keywords.isNotEmpty())) {
                rules.add(GitleaksRule(id, description, compiledPattern, keywords, entropy, ruleAllowlists))
            }
        }

        // 3. Inject Rules for URLs, Endpoints, IPs, and DB Strings
        injectExtendedNetworkAndEndpointRules(rules)

        val globalAllowlist = GitleaksAllowlist(
            pathRegexes = allowlistPaths,
            globalRegexes = allowlistRegexes,
            stopwords = allowlistStopwords
        )

        return GitleaksConfig(globalAllowlist, rules)
    }

    /**
     * Appends rules for Web Links, REST API Endpoints, IPs, WebSockets, and Database URIs.
     */
    private fun injectExtendedNetworkAndEndpointRules(rules: MutableList<GitleaksRule>) {
        val extendedRules = listOf(
            // HTTP / HTTPS Web URLs & Endpoints
            GitleaksRule(
                id = "http-https-url",
                description = "Discovered HTTP/HTTPS Web URL or Remote Server Endpoint.",
                regex = Pattern.compile("(?i)\\bhttps?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?::\\d+)?(?:/\\S*)?\\b"),
                keywords = listOf("http://", "https://")
            ),
            // WebSocket Connection Endpoints
            GitleaksRule(
                id = "websocket-url",
                description = "Discovered WebSocket URL Endpoint.",
                regex = Pattern.compile("(?i)\\bwss?://[a-zA-Z0-9.-]+(?::\\d+)?(?:/\\S*)?\\b"),
                keywords = listOf("ws://", "wss://")
            ),
            // REST API Relative Endpoint Paths (e.g., "/api/v1/auth/login", "/v2/users")
            GitleaksRule(
                id = "rest-api-endpoint",
                description = "Discovered REST API Route Path.",
                regex = Pattern.compile("(?i)^/(?:api|v[0-9]+|auth|user|payment|admin|service|oauth)/[a-zA-Z0-9_/.-]+$"),
                keywords = listOf("/api/", "/v1/", "/v2/", "/auth/", "/user/", "/admin/")
            ),
            // IPv4 Host Address
            GitleaksRule(
                id = "ipv4-address",
                description = "Discovered Explicit IPv4 Address Endpoint.",
                regex = Pattern.compile("\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(?::\\d+)?\\b"),
                keywords = listOf(".")
            ),
            // Database URIs (JDBC, MySQL, MongoDB, PostgreSQL, Redis)
            GitleaksRule(
                id = "database-uri",
                description = "Discovered Database Connection Connection String / URI.",
                regex = Pattern.compile("(?i)\\b(?:jdbc|mongodb(?:\\+srv)?|postgresql|mysql|redis)://\\S+\\b"),
                keywords = listOf("jdbc:", "mongodb://", "postgres://", "mysql://", "redis://")
            )
        )

        rules.addAll(extendedRules)
    }

    private fun tryCompile(regex: String): Pattern? {
        if (regex.isBlank()) return null
        return try {
            Pattern.compile(regex)
        } catch (_: Exception) {
            val cleaned = regex.replace("(?i)", "")
                .replace("[:alnum:]", "a-zA-Z0-9")
                .replace("[:alpha:]", "a-zA-Z")
                .replace("(?-i:", "(")
            try {
                Pattern.compile(cleaned, Pattern.CASE_INSENSITIVE)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun extractTomlValue(block: String, key: String): String {
        val line = block.lines().firstOrNull { it.trim().startsWith("$key =") } ?: return ""
        return line.substringAfter("=").trim().removeSurrounding("\"").removeSurrounding("'")
    }

    private fun extractTomlRawRegex(block: String): String {
        if (!block.contains("regex =")) return ""
        val afterRegex = block.substringAfter("regex =").trim()
        return if (afterRegex.startsWith("'''")) {
            afterRegex.substringAfter("'''").substringBefore("'''").trim()
        } else if (afterRegex.startsWith("\"\"\"")) {
            afterRegex.substringAfter("\"\"\"").substringBefore("\"\"\"").trim()
        } else {
            afterRegex.lines().first().removeSurrounding("\"").removeSurrounding("'").trim()
        }
    }

    private fun extractTomlList(block: String, key: String): List<String> {
        if (!block.contains("$key =")) return emptyList()
        val afterKey = block.substringAfter("$key =").trim()
        val listContent = afterKey.substringAfter("[").substringBefore("]")
        return listContent.split("\n", ",")
            .map { it.trim().removeSurrounding("\"").removeSurrounding("'").removeSurrounding("'''") }
            .filter { it.isNotBlank() }
    }
}
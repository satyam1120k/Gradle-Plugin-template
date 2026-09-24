package com.guard.transformer.encryption

object SecretPatternDatabase {

    var config: GitleaksConfig? = null
        private set

    init {
        loadGitleaksConfig()
    }

    private fun loadGitleaksConfig() {
        val path = "gitleaks.toml"
        val stream = javaClass.classLoader.getResourceAsStream(path) ?: javaClass.getResourceAsStream(path)

        if (stream != null) {
            try {
                val parsedConfig = GitleaksConfigParser.parse(stream)
                if (parsedConfig.rules.isNotEmpty()) {
                    config = parsedConfig
                }
            } catch (_: Exception) {
                // Handle or log the exception if parsing fails
            }
        }
    }
}

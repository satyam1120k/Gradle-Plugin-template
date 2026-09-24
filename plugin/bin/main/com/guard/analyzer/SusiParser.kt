package com.guard.analyzer

import org.gradle.api.GradleException
import java.io.InputStream

class SusiParser {
    val sources = mutableMapOf<String, String>() // signature -> category
    val sinks = mutableMapOf<String, String>()   // signature -> category

    init {
        loadBundledDatasets()
    }

    private fun loadBundledDatasets() {
        // Load from plugin's internal resources packaged inside the JAR
        val sourcesStream = javaClass.classLoader.getResourceAsStream("Ouput_CatSources_v0_9.txt")
        if (sourcesStream == null) {
            throw GradleException("IntelliGuard Error: Bundled sources dataset 'Ouput_CatSources_v0_9.txt' not found inside plugin resources.")
        }

        val sinksStream = javaClass.classLoader.getResourceAsStream("Ouput_CatSinks_v0_9.txt")
        if (sinksStream == null) {
            throw GradleException("IntelliGuard Error: Bundled sinks dataset 'Ouput_CatSinks_v0_9.txt' not found inside plugin resources.")
        }

        loadStream(sourcesStream, sources)
        loadStream(sinksStream, sinks)
    }

    private fun loadStream(inputStream: InputStream, map: MutableMap<String, String>) {
        inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val parts = trimmed.split("->")
                    if (parts.size == 2) {
                        val signature = parts[0].trim()
                        val category = parts[1].trim()
                        map[signature] = category
                    }
                }
            }
        }
    }
}
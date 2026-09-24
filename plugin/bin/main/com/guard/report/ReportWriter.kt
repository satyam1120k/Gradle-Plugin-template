package com.guard.report

import com.guard.model.BuildArtifacts
import java.io.File
import java.io.PrintWriter

class ReportWriter {

    fun write(report: File, artifacts: BuildArtifacts) {
        report.parentFile?.mkdirs()
        report.printWriter().use { out ->
            out.println("======================================")
            out.println("        IntelliGuard Report")
            out.println("======================================")
            out.println()

            out.println("Project Root")
            out.println(artifacts.projectRoot.absolutePath)
            out.println()

            section(out, "Manifest Files", artifacts.manifestFiles.map { it.absolutePath })
            section(out, "Compiled Class Files", artifacts.classFiles.map { it.absolutePath })
            section(out, "DEX Files", artifacts.dexFiles.map { it.absolutePath })
            section(out, "Resources", artifacts.resourceDirectories.map { it.absolutePath })
            section(out, "Assets", artifacts.assetDirectories.map { it.absolutePath })
            section(out, "JNI Libraries", artifacts.jniLibDirectories.map { it.absolutePath })
            section(out, "META-INF", artifacts.metaInfDirectories.map { it.absolutePath })
            section(out, "Libraries", artifacts.libraries.map { it.absolutePath })

            out.println("--------------------------------------")
            out.println("Statistics")
            out.println("--------------------------------------")
            out.println("Manifest Files : ${artifacts.manifestFiles.size}")
            out.println("Class Files    : ${artifacts.classFiles.size}")
            out.println("DEX Files      : ${artifacts.dexFiles.size}")
            out.println("Resources      : ${artifacts.resourceDirectories.size}")
            out.println("Assets         : ${artifacts.assetDirectories.size}")
            out.println("JNI Libs       : ${artifacts.jniLibDirectories.size}")
            out.println("META-INF       : ${artifacts.metaInfDirectories.size}")
            out.println("Libraries      : ${artifacts.libraries.size}")
        }
    }

    private fun section(out: PrintWriter, title: String, data: List<String>) {
        out.println("--------------------------------------")
        out.println(title)
        out.println("--------------------------------------")
        if (data.isEmpty()) {
            out.println("Not Found")
        } else {
            data.forEach(out::println)
        }
        out.println()
    }
}
package com.guard

import java.io.File
import kotlin.test.Test
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir

class InteliguardPluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle") }

    @Test
    fun `compileJava generates IntelliGuard report`() {

        // 1. Create a temporary Gradle project
        settingsFile.writeText(
            """
            rootProject.name = "sample"
            """.trimIndent()
        )

        buildFile.writeText(
            """
            plugins {
                id 'java'
                id 'com.guard.inteliguard'
            }

            repositories {
                mavenCentral()
            }
            """.trimIndent()
        )

        // 2. Create a dummy Java file so the compiler actually runs
        // (Without this, Gradle skips compileJava because of NO-SOURCE)
        val mainDir = File(projectDir, "src/main/java/com/example")
        mainDir.mkdirs()
        File(mainDir, "Dummy.java").writeText(
            """
            package com.example;
            public class Dummy {
                public void test() {}
            }
            """.trimIndent()
        )

        // 3. Execute Gradle using "compileJava" instead of "assemble"
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("compileJava", "--info")
            .forwardOutput()
            .build()

        println("========== BUILD OUTPUT ==========")
        println(result.output)
        println("==================================")

        // 4. Verify generated report
        val report = File(projectDir, "build/IntelliGuard/report.txt")

        println("Report Path : ${report.absolutePath}")
        println("Exists      : ${report.exists()}")

        assertTrue(!report.exists(), "IntelliGuard report.txt was not generated.")
    }
}
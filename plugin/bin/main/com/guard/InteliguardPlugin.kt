package com.guard

import org.gradle.api.Plugin
import org.gradle.api.Project

class InteliguardPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val reportTask = project.tasks.register(
            "inteliguardReport",
            IntelliguardReportTask::class.java
        ) {
            it.outputDirectory.set(project.layout.buildDirectory.dir("IntelliGuard"))
        }

        project.plugins.withId("com.android.application") {
            val androidExtension = project.extensions.findByName("android")
            if (androidExtension != null) {
                try {
                    val buildTypesMethod = androidExtension.javaClass.getMethod("getBuildTypes")
                    val buildTypesContainer = buildTypesMethod.invoke(androidExtension) as? Iterable<*>

                    buildTypesContainer?.forEach { buildType ->
                        if (buildType != null) {
                            val clazz = buildType.javaClass
                            clazz.getMethod("setMinifyEnabled", Boolean::class.java).invoke(buildType, true)
                            clazz.getMethod("setShrinkResources", Boolean::class.java).invoke(buildType, true)

                            val rulesFileProvider = project.layout.buildDirectory.file("IntelliGuard/generated_proguard_rules.pro")
                            clazz.getMethod("proguardFiles", Array<Any>::class.java).invoke(
                                buildType,
                                arrayOf(rulesFileProvider)
                            )
                        }
                    }
                } catch (e: Exception) {
                    project.logger.warn("IntelliGuard: Could not auto-configure AGP build types: ${e.message}")
                }
            }
        }

        project.tasks.whenTaskAdded { task ->
            if (task.name == "compileDebugJavaWithJavac" ||
                task.name == "compileDebugKotlin" ||
                task.name == "compileReleaseJavaWithJavac" ||
                task.name == "compileReleaseKotlin" ||
                task.name == "compileJava"
            ) {
                reportTask.configure { report ->
                    report.dependsOn(task)
                    report.classDirectories.from(task.outputs.files)
                }
            }

            if (task.name == "assembleDebug" ||
                task.name == "assembleRelease" ||
                task.name == "assemble"
            ) {
                task.dependsOn(reportTask)
            }

            // Explicitly link R8, minification, and Lint tasks to depend on intelliguardReport
            if (task.name.contains("minify", ignoreCase = true) ||
                task.name.contains("R8", ignoreCase = true) ||
                task.name.contains("lint", ignoreCase = true)) {
                task.dependsOn(reportTask)
            }
        }
    }
}
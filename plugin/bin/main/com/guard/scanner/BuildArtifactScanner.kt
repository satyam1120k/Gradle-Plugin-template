package com.guard.scanner

import com.guard.model.BuildArtifacts
import java.io.File

class BuildArtifactScanner {

    fun scan(projectRoot: File, classDirs: List<File>): BuildArtifacts {
        val intermediatesDir = File(projectRoot, "build/intermediates")

        // 1. Manifest Files
        val manifestFiles = mutableListOf<File>()
        if (intermediatesDir.exists()) {
            intermediatesDir.walkTopDown().forEach { file ->
                if (file.isFile && file.name.equals("AndroidManifest.xml", ignoreCase = true)) {
                    manifestFiles.add(file)
                }
            }
        }
        val mainManifest = File(projectRoot, "src/main/AndroidManifest.xml")
        if (mainManifest.exists() && !manifestFiles.contains(mainManifest)) {
            manifestFiles.add(mainManifest)
        }

        // 2. Class Files
        val classFiles = classDirs.flatMap { dir ->
            if (dir.exists()) {
                dir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
            } else {
                emptyList()
            }
        }

        // 3. DEX Files (Excluded)
        val dexFiles = emptyList<File>()

        // 4. Resources
        val resourceFiles = mutableListOf<File>()
        listOf(
            File(projectRoot, "build/intermediates/merged_res"),
            File(projectRoot, "build/intermediates/processed_res"),
            File(projectRoot, "src/main/res")
        ).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().forEach { if (it.isFile) resourceFiles.add(it) }
            }
        }

        // 5. Assets
        val assetFiles = mutableListOf<File>()
        listOf(
            File(projectRoot, "build/intermediates/merged_assets"),
            File(projectRoot, "src/main/assets")
        ).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().forEach { if (it.isFile) assetFiles.add(it) }
            }
        }

        // 6. JNI / Native Libraries
        val jniFiles = mutableListOf<File>()
        listOf(
            File(projectRoot, "build/intermediates/merged_native_libs"),
            File(projectRoot, "build/intermediates/stripped_native_libs")
        ).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().forEach {
                    if (it.isFile && (it.extension == "so" || it.extension == "jni")) {
                        jniFiles.add(it)
                    }
                }
            }
        }

        // 7. META-INF
        val metaInfFiles = mutableListOf<File>()
        if (intermediatesDir.exists()) {
            intermediatesDir.walkTopDown().forEach { file ->
                if (file.isDirectory && file.name.equals("META-INF", ignoreCase = true)) {
                    file.walkTopDown().forEach { if (it.isFile) metaInfFiles.add(it) }
                }
            }
        }

        // 8. Libraries
        val libraryFiles = mutableListOf<File>()
        listOf(
            File(projectRoot, "build/intermediates/compile_classpath_libs"),
            File(projectRoot, "build/intermediates/runtime_jars"),
            File(projectRoot, "libs")
        ).forEach { dir ->
            if (dir.exists()) {
                dir.walkTopDown().forEach {
                    if (it.isFile && (it.extension == "jar" || it.extension == "aar")) {
                        libraryFiles.add(it)
                    }
                }
            }
        }

        return BuildArtifacts(
            projectRoot = projectRoot,
            manifestFiles = manifestFiles,
            classFiles = classFiles,
            dexFiles = dexFiles,
            resourceDirectories = resourceFiles,
            assetDirectories = assetFiles,
            jniLibDirectories = jniFiles,
            metaInfDirectories = metaInfFiles,
            libraries = libraryFiles
        )
    }
}
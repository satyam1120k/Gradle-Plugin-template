package com.guard.model

import java.io.File

data class BuildArtifacts(
    val projectRoot: File,
    val manifestFiles: List<File>,
    val classFiles: List<File>,
    val dexFiles: List<File>,
    val resourceDirectories: List<File>,
    val assetDirectories: List<File>,
    val jniLibDirectories: List<File>,
    val metaInfDirectories: List<File>,
    val libraries: List<File>
)
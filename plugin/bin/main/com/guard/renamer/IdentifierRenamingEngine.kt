package com.guard.renamer

import com.guard.model.RichGraphDataset
import com.guard.renamer.engine.RenameMapBuilder
import java.io.File

object IdentifierRenamingEngine {

    fun executeRenaming(classFiles: List<File>, dataset: RichGraphDataset, outputDir: File) {
        // 1. Build the global rename mapping dictionary
        val renameMap = RenameMapBuilder.buildMap(classFiles, dataset)

        // 2. Write mapping audit log file
        MappingWriter.writeReport(outputDir, renameMap)

        // 3. Execute ASM bytecode transformations on class files
        BytecodeTransformer.transformClasses(classFiles, renameMap)
    }
}
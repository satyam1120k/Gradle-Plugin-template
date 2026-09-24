package com.guard.renamer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import java.io.File

object BytecodeTransformer {

    fun transformClasses(classFiles: List<File>, renameMap: Map<String, String>) {
        classFiles.forEach { classFile ->
            try {
                val originalBytes = classFile.readBytes()
                val classReader = ClassReader(originalBytes)
                val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)

                val remapper = IntelliGuardRemapper(renameMap)
                val classRemapper = ClassRemapper(classWriter, remapper)

                classReader.accept(classRemapper, ClassReader.EXPAND_FRAMES)
                classFile.writeBytes(classWriter.toByteArray())
            } catch (_: Exception) {
                // Ignored safely
            }
        }
    }
}
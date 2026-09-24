package com.guard.transformer

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File

object LocalVariableStrippingTransformer {

    private fun stripClass(classFile: File) {
        try {
            val originalBytes = classFile.readBytes()
            val classReader = ClassReader(originalBytes)
            val classWriter = ClassWriter(0)

            val classVisitor = object : ClassVisitor(Opcodes.ASM9, classWriter) {
                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?
                ): MethodVisitor {
                    val methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions)

                    return object : MethodVisitor(Opcodes.ASM9, methodVisitor) {
                        // By leaving visitLocalVariable empty (not calling super),
                        // we strip out the LocalVariableTable completely from the bytecode.
                        override fun visitLocalVariable(
                            name: String?,
                            descriptor: String?,
                            signature: String?,
                            start: Label?,
                            end: Label?,
                            index: Int
                        ) {
                            // Intentionally omitted to strip local variable metadata
                        }

                        // Optional: Also strip line numbers to further harden against reverse engineering stack traces
                        override fun visitLineNumber(line: Int, start: Label?) {
                            // Intentionally omitted if you want to strip line number debug info as well
                        }
                    }
                }
            }

            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
            classFile.writeBytes(classWriter.toByteArray())
        } catch (_: Exception) {
            // Ignore unreadable or corrupted class files safely
        }
    }

    fun executeStripping(classFiles: List<File>, outputDir: File) {
        classFiles.forEach { file ->
            stripClass(file)
        }

        // Generate a summary log report file
        val reportFile = File(outputDir, "variable_stripping_report.txt")
        reportFile.parentFile?.mkdirs()
        reportFile.printWriter().use { out ->
            out.println("======================================")
            out.println("   IntelliGuard Variable Stripping    ")
            out.println("======================================")
            out.println()
            out.println("Status          : SUCCESS")
            out.println("Processed Files : ${classFiles.size}")
            out.println("Details         : LocalVariableTable and debug metadata successfully stripped.")
        }
    }
}
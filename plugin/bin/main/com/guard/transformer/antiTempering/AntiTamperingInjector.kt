package com.guard.transformer.antiTempering

import com.guard.model.RichGraphDataset
import com.guard.renamer.config.ProtectedRules
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.io.File

object AntiTamperingInjector {

    fun execute(
        classFiles: List<File>,
        dataset: RichGraphDataset,
        outputDir: File
    ) {
        val reportFile = File(outputDir, "anti_tempering_report.txt")
        reportFile.parentFile?.mkdirs()

        val targetMethods = dataset.nodes.filter { node ->
            node.riskCategory.equals("Critical", ignoreCase = true) ||
                    node.obfuscationPlan.any { action -> action.contains("anti-tempering", ignoreCase = true) }
        }.map { it.id }.toSet()

        var protectedMethodsCount = 0
        val injectionLogs = mutableListOf<String>()

        classFiles.forEach { classFile ->
            try {
                val bytes = classFile.readBytes()
                val classReader = ClassReader(bytes)
                val classNode = ClassNode()
                classReader.accept(classNode, 0)

                val internalName = classNode.name
                val simpleName = internalName.substringAfterLast('/')

                if (!ProtectedRules.isProtectedClass(simpleName, internalName) && !internalName.contains("$")) {
                    var classModified = false

                    classNode.methods.forEach { method ->
                        val cleanClassName = internalName.replace('/', '.')
                        val methodId = "$cleanClassName.${method.name}"

                        if (targetMethods.contains(methodId) && method.instructions.size() > 0) {
                            // Inject call to IntelliGuardSecurity.isTampered() (Signature & Integrity Verification)
                            val checkSequence = buildTamperCheckInsnList()
                            method.instructions.insert(checkSequence)

                            protectedMethodsCount++
                            classModified = true
                            injectionLogs.add("Injected Anti-Tampering & Integrity Check into Critical Method: $methodId")
                        }
                    }

                    if (classModified) {
                        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                        classNode.accept(classWriter)
                        classFile.writeBytes(classWriter.toByteArray())
                    }
                }
            } catch (_: Exception) {
                // Ignore unreadable files
            }
        }

        AntiTamperingReportWriter.writeReport(reportFile, targetMethods.size, protectedMethodsCount, injectionLogs)
    }

    private fun buildTamperCheckInsnList(): InsnList {
        val safeLabel = LabelNode()

        return InsnList().apply {
            // INVOKESTATIC com/guard/runtime/IntelliGuardSecurity.isTampered ()Z
            add(
                MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/guard/runtime/IntelliGuardSecurity",
                    "isTampered",
                    "()Z",
                    false
                )
            )

            // If result is false (0), jump to safeLabel and proceed normally
            add(
                JumpInsnNode(
                    Opcodes.IFEQ,
                    safeLabel
                )
            )

            // If result is true (tampered / re-signed / debugged), throw SecurityException and crash execution
            add(TypeInsnNode(Opcodes.NEW, "java/lang/SecurityException"))
            add(InsnNode(Opcodes.DUP))
            add(LdcInsnNode("Application tampering or integrity violation detected! Execution terminated."))
            add(
                MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "java/lang/SecurityException",
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false
                )
            )
            add(InsnNode(Opcodes.ATHROW))

            add(safeLabel)
        }
    }
}
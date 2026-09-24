package com.guard.transformer.controlFlowFlattening

import com.guard.model.RichGraphDataset
import com.guard.renamer.config.ProtectedRules
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.Random

object ControlFlowFlatteningTransformer {

    fun execute(
        classFiles: List<File>,
        dataset: RichGraphDataset,
        outputDir: File
    ) {
        val reportFile = File(outputDir, "control_flow_flattening_report.txt")
        reportFile.parentFile?.mkdirs()

        // Target Critical Risk Methods as defined by RiskEvaluator
        val targetMethods = dataset.nodes.filter { node ->
            node.riskCategory.equals("Critical", ignoreCase = true) ||
                    node.obfuscationPlan.any { action ->
                        action.contains("Control Flow Flattening", ignoreCase = true)
                    }
        }.map { it.id }.toSet()

        var flattenedMethodsCount = 0
        val reportLogs = mutableListOf<String>()

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

                    classNode.methods.forEach { methodNode ->
                        val methodId = "${internalName.replace('/', '.')}.${methodNode.name}"

                        if (targetMethods.contains(methodId) && methodNode.instructions.size() > 10) {
                            // Avoid methods with Try-Catch as CFF natively destroys exception boundaries
                            if (methodNode.tryCatchBlocks.isEmpty()) {
                                val seed = "$methodId-cff".hashCode().toLong()
                                val random = Random(seed)

                                if (ControlFlowFlattener.flattenMethod(methodNode, classNode.name, random)) {
                                    flattenedMethodsCount++
                                    classModified = true
                                    reportLogs.add("Successfully Flattened: $methodId")
                                }
                            }
                        }
                    }

                    if (classModified) {
                        // COMPUTE_FRAMES recomputes the new stack map frames after we destroy the CFG
                        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                        classNode.accept(classWriter)
                        classFile.writeBytes(classWriter.toByteArray())
                    }
                }
            } catch (_: Exception) {
                // Safely ignore unreadable files
            }
        }
        ControlFlowReportWriter.writeReport(reportFile, targetMethods.size, flattenedMethodsCount, reportLogs)
    }
}
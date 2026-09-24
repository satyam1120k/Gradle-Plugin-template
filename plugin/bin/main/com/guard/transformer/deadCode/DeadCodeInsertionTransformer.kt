package com.guard.transformer.deadCode

import com.guard.model.RichGraphDataset
import com.guard.renamer.config.ProtectedRules
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.Random

object DeadCodeInsertionTransformer {

    fun execute(
        classFiles: List<File>,
        dataset: RichGraphDataset,
        outputDir: File
    ) {
        val reportFile = File(outputDir, "dead_code_insertion_report.txt")

        // 1. Target ONLY High and Critical Risk Methods based on AI risk evaluation or planning actions
        val targetMethods = dataset.nodes.filter { node ->
            node.riskCategory.equals("Critical", ignoreCase = true) ||
                    node.riskCategory.equals("High", ignoreCase = true) ||
                    node.obfuscationPlan.any { action -> action.contains("Junk Code Insertion", ignoreCase = true) }
        }.map { it.id }.toSet()

        var transformedMethodCount = 0
        val injectionLogs = mutableListOf<DeadCodeInjectionLog>()

        classFiles.forEach { classFile ->
            try {
                val bytes = classFile.readBytes()
                val classReader = ClassReader(bytes)
                val classNode = ClassNode()
                classReader.accept(classNode, 0)

                val internalName = classNode.name
                val simpleName = internalName.substringAfterLast('/')

                // Skip system and generated boilerplate classes
                if (!ProtectedRules.isProtectedClass(simpleName, internalName) && !internalName.contains("$")) {
                    var classModified = false

                    classNode.methods.forEach { method ->
                        val cleanClassName = internalName.replace('/', '.')
                        val methodId = "$cleanClassName.${method.name}"

                        // 2. Process only if the method is targeted (High/Critical) and has instructions
                        if (targetMethods.contains(methodId) && method.instructions.size() > 5) {
                            val uniqueString = "$methodId-deadcode"
                            val seededRandom = Random(uniqueString.hashCode().toLong())

                            // 3. Fetch a random polymorphic dead code pattern
                            val (patternName, patternGenerator) = DeadCodeTemplates.getRandomPattern(seededRandom)
                            val deadCodeBlock = patternGenerator(seededRandom)

                            // Insert the dead code block right at the start of the method body
                            method.instructions.insert(deadCodeBlock)

                            classModified = true
                            transformedMethodCount++

                            injectionLogs.add(
                                DeadCodeInjectionLog(
                                    className = cleanClassName,
                                    methodName = method.name,
                                    patternUsed = patternName
                                )
                            )
                        }
                    }

                    if (classModified) {
                        // COMPUTE_FRAMES automatically recomputes stack maps and stack sizes
                        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                        classNode.accept(classWriter)
                        classFile.writeBytes(classWriter.toByteArray())
                    }
                }
            } catch (_: Exception) {
                // Safely ignore unreadable or corrupt class files
            }
        }

        // 4. Output the structured audit report
        DeadCodeReportWriter.writeReport(reportFile, targetMethods.size, transformedMethodCount, injectionLogs)
    }
}
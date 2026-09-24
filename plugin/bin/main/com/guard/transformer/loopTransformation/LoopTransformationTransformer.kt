package com.guard.transformer.loopTransformation

import com.guard.model.RichGraphDataset
import com.guard.renamer.config.ProtectedRules
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.io.File
import java.util.Random

object LoopTransformationTransformer {

    fun execute(
        classFiles: List<File>,
        dataset: RichGraphDataset,
        outputDir: File
    ) {
        val reportFile = File(outputDir, "loop_transformation_report.txt")
        reportFile.parentFile?.mkdirs()

        // 1. Target ONLY High and Critical Risk Methods based on AI prediction
        val targetMethods = dataset.nodes.filter { node ->
            node.riskCategory.equals("Critical", ignoreCase = true) ||
                    node.riskCategory.equals("High", ignoreCase = true)
        }.map { it.id }.toSet()

        var transformedMethodCount = 0
        var scrambledLoopCount = 0
        val reportLogs = mutableListOf<String>()

        classFiles.forEach { classFile ->
            try {
                val bytes = classFile.readBytes()
                val classReader = ClassReader(bytes)
                val classNode = ClassNode()
                classReader.accept(classNode, 0)

                val internalName = classNode.name
                val simpleName = internalName.substringAfterLast('/')

                // Skip system and dynamically generated boilerplate
                if (!ProtectedRules.isProtectedClass(simpleName, internalName) && !internalName.contains("$")) {
                    var classModified = false

                    classNode.methods.forEach { method ->
                        val methodId = "${internalName.replace('/', '.')}.${method.name}"

                        // 2. Process only if it's a High/Critical target
                        if (targetMethods.contains(methodId) && method.instructions.size() > 0) {
                            val iincInstructions = mutableListOf<IincInsnNode>()

                            // Look for traditional loop counters (i++)
                            val iterator = method.instructions.iterator()
                            while (iterator.hasNext()) {
                                val insn = iterator.next()
                                if (insn is IincInsnNode) {
                                    iincInstructions.add(insn)
                                }
                            }

                            if (iincInstructions.isNotEmpty()) {
                                iincInstructions.forEachIndexed { index, iincNode ->

                                    // 3. Create a Seeded Deterministic Random for Build Caching & Consistency
                                    val uniqueString = "$methodId-var${iincNode.`var`}-incr${iincNode.incr}-idx$index"
                                    val seededRandom = Random(uniqueString.hashCode().toLong())

                                    // 4. Replace IINC with Polymorphic Opaque Predicates
                                    val scrambledSequence = buildPolymorphicIncrement(iincNode.`var`, iincNode.incr, seededRandom)

                                    method.instructions.insertBefore(iincNode, scrambledSequence)
                                    method.instructions.remove(iincNode)

                                    scrambledLoopCount++
                                }

                                classModified = true
                                transformedMethodCount++
                                reportLogs.add("Transformed Method: $methodId (Scrambled ${iincInstructions.size} loop indices)")
                            }
                        }
                    }

                    if (classModified) {
                        // COMPUTE_FRAMES automatically recomputes both maximum stack sizes and JVM stack map frames
                        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                        classNode.accept(classWriter)
                        classFile.writeBytes(classWriter.toByteArray())
                    }
                }
            } catch (_: Exception) {
                // Safely ignore corrupt or unreadable classes
            }
        }

        // 5. Output the audit report with specific method names
        writeReport(reportFile, targetMethods.size, transformedMethodCount, scrambledLoopCount, reportLogs)
    }

    /**
     * Polymorphic Opaque Predicate Generator using Seeded Random.
     * Randomly applies one of three complex mathematical formulas evaluating to the original increment.
     */
    private fun buildPolymorphicIncrement(varIndex: Int, increment: Int, random: Random): InsnList {
        val insnList = InsnList()

        // Load current loop counter variable 'i' onto the stack
        insnList.add(VarInsnNode(Opcodes.ILOAD, varIndex))

        // Randomly pick one of 3 complex mathematical strategies deterministically
        val strategy = random.nextInt(3)

        when (strategy) {
            0 -> {
                // STRATEGY 0: Variable-Dependent Nullification (The Strongest)
                // Math: increment + (i ^ i) -> Relies on runtime state, blocking decompiler constant folding
                insnList.add(LdcInsnNode(increment))
                insnList.add(VarInsnNode(Opcodes.ILOAD, varIndex))
                insnList.add(VarInsnNode(Opcodes.ILOAD, varIndex))
                insnList.add(InsnNode(Opcodes.IXOR))
                insnList.add(InsnNode(Opcodes.IADD))
            }
            1 -> {
                // STRATEGY 1: Triple XOR Chaining
                // Math: Key1 ^ Key2 ^ Key3 == increment
                val key1 = random.nextInt(9000) + 1000
                val key2 = random.nextInt(9000) + 1000
                val key3 = (key1 xor key2) xor increment

                insnList.add(LdcInsnNode(key1))
                insnList.add(LdcInsnNode(key2))
                insnList.add(InsnNode(Opcodes.IXOR))
                insnList.add(LdcInsnNode(key3))
                insnList.add(InsnNode(Opcodes.IXOR))
            }
            2 -> {
                // STRATEGY 2: Bitwise Shift & Subtraction Combination
                // Math: (Key1 << 1) - Key2 == increment
                val key1 = random.nextInt(4000) + 1000
                val key2 = (key1 shl 1) - increment

                insnList.add(LdcInsnNode(key1))
                insnList.add(InsnNode(Opcodes.ICONST_1))
                insnList.add(InsnNode(Opcodes.ISHL))
                insnList.add(LdcInsnNode(key2))
                insnList.add(InsnNode(Opcodes.ISUB))
            }
        }

        // Add the evaluated increment to 'i' and save it back
        insnList.add(InsnNode(Opcodes.IADD))
        insnList.add(VarInsnNode(Opcodes.ISTORE, varIndex))

        return insnList
    }

    private fun writeReport(
        reportFile: File,
        eligibleMethods: Int,
        transformedMethods: Int,
        totalScrambled: Int,
        logs: List<String>
    ) {
        reportFile.printWriter().use { out ->
            out.println("==================================================")
            out.println(" IntelliGuard Loop Transformation Report          ")
            out.println("==================================================")
            out.println("Eligible Target Methods (High/Critical) : $eligibleMethods")
            out.println("Methods Actively Transformed            : $transformedMethods")
            out.println("Total Loop Indices Scrambled            : $totalScrambled")
            out.println("==================================================")
            out.println()

            if (logs.isEmpty()) {
                out.println("No loops detected in the targeted High/Critical methods.")
            } else {
                out.println("Detailed Transformation Log (Targeted Methods):")
                out.println("--------------------------------------------------")
                logs.forEach { log ->
                    out.println("- $log")
                }
            }
        }
    }
}
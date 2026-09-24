package com.guard.transformer.controlFlowObfs

import com.guard.model.RichGraphDataset
import com.guard.renamer.config.ProtectedRules
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import java.io.File

object InstructionSubstitutionTransformer {

    fun execute(
        classFiles: List<File>,
        dataset: RichGraphDataset,
        outputDir: File
    ) {
        val reportFile = File(outputDir, "instruction_substitution_report.txt")
        reportFile.parentFile?.mkdirs()

        val targetMethods = dataset.nodes.filter { node ->
            node.riskCategory.equals("Critical", ignoreCase = true) ||
                    node.riskCategory.equals("High", ignoreCase = true) ||
                    node.obfuscationPlan.any { action ->
                        action.contains("Instruction Substitution", ignoreCase = true) ||
                                action.contains("peephole", ignoreCase = true)
                    }
        }.map { it.id }.toSet()

        var substitutedInstructionsCount = 0
        var splitBlocksCount = 0

        classFiles.forEach { classFile ->
            try {
                val bytes = classFile.readBytes()
                val classReader = ClassReader(bytes)
                val classNode = ClassNode()
                classReader.accept(classNode, 0)

                val internalName = classNode.name
                val simpleName = internalName.substringAfterLast('/')

                if (!ProtectedRules.isProtectedClass(simpleName, internalName) && !internalName.contains("$")) {
                    var modified = false

                    classNode.methods.forEach { method ->
                        val methodId = "${internalName.replace('/', '.')}.${method.name}"

                        if (targetMethods.contains(methodId) && method.instructions.size() > 0) {
                            // 1. Substitute Arithmetic Instructions via Factory
                            val insnToReplace = mutableListOf<InsnNode>()
                            val iterator = method.instructions.iterator()

                            while (iterator.hasNext()) {
                                val insn = iterator.next()
                                if (insn is InsnNode && ArithmeticSubstitutionFactory.isSubstitutable(insn.opcode)) {
                                    insnToReplace.add(insn)
                                }
                            }

                            insnToReplace.forEach { targetInsn ->
                                val replacement = ArithmeticSubstitutionFactory.createSubstitutedSequence(targetInsn.opcode)
                                if (replacement != null) {
                                    method.instructions.insertBefore(targetInsn, replacement)
                                    method.instructions.remove(targetInsn)
                                    substitutedInstructionsCount++
                                    modified = true
                                }
                            }

                            // 2. Basic Block Splitting Pass
                            val splitCount = splitBasicBlocks(method)
                            if (splitCount > 0) {
                                splitBlocksCount += splitCount
                                modified = true
                            }
                        }
                    }

                    if (modified) {
                        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                        classNode.accept(classWriter)
                        classFile.writeBytes(classWriter.toByteArray())
                    }
                }
            } catch (_: Exception) {
                // Ignore corrupt or unreadable class files safely
            }
        }
        reportFile.writeText("IntelliGuard Instruction Substitution & Splitting Summary\n")
        reportFile.appendText("========================================================\n")
        reportFile.appendText("Targeted Methods             : ${targetMethods.size}\n")
        reportFile.appendText("Substituted Arithmetic Ops   : $substitutedInstructionsCount\n")
        reportFile.appendText("Created Basic Block Splits   : $splitBlocksCount\n")
    }

    private fun splitBasicBlocks(methodNode: MethodNode): Int {
        var splitsCreated = 0
        val instructions = methodNode.instructions
        val size = instructions.size()

        if (size > 15) {
            var count = 0
            val targetsToSplit = mutableListOf<AbstractInsnNode>()

            val iterator = instructions.iterator()
            while (iterator.hasNext()) {
                val insn = iterator.next()
                count++
                if (count % 10 == 0 && insn.next != null && insn.opcode != Opcodes.GOTO && insn !is LabelNode) {
                    targetsToSplit.add(insn)
                }
            }
            targetsToSplit.forEach { target ->
                val splitLabel = LabelNode()
                val jumpInsn = JumpInsnNode(Opcodes.GOTO, splitLabel)

                val splitBlock = InsnList().apply {
                    add(jumpInsn)
                    add(splitLabel)
                }

                instructions.insert(target, splitBlock)
                splitsCreated++
            }
        }
        return splitsCreated
    }
}
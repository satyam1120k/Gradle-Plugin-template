package com.guard.transformer.passes

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.JumpInsnNode
import org.objectweb.asm.tree.VarInsnNode

object DeadCodeEliminationOptimizer {

    fun optimize(classNode: ClassNode): Boolean {
        var modified = false

        classNode.methods.forEach { method ->
            val instructions = method.instructions
            if (instructions.size() >= 2) {
                val iterator = instructions.iterator()
                var previousInsn = iterator.next()

                while (iterator.hasNext()) {
                    val currentInsn = iterator.next()

                    // Rule: Redundant Load and Store Elimination (ILOAD X -> ISTORE X)
                    if (previousInsn is VarInsnNode && currentInsn is VarInsnNode) {
                        if (isLoadOpcode(previousInsn.opcode) &&
                            isStoreOpcode(currentInsn.opcode) &&
                            previousInsn.`var` == currentInsn.`var`) {
                            previousInsn.opcode = Opcodes.NOP
                            currentInsn.opcode = Opcodes.NOP
                            modified = true
                        }
                    }

                    // Rule: Redundant GOTO Elimination
                    if (currentInsn is JumpInsnNode && currentInsn.opcode == Opcodes.GOTO) {
                        val targetLabelNode = currentInsn.label
                        if (targetLabelNode != null && targetLabelNode == currentInsn.next) {
                            currentInsn.opcode = Opcodes.NOP
                            modified = true
                        }
                    }

                    previousInsn = currentInsn
                }
            }
        }
        return modified
    }

    private fun isLoadOpcode(opcode: Int): Boolean {
        return opcode in Opcodes.ILOAD..Opcodes.ALOAD
    }

    private fun isStoreOpcode(opcode: Int): Boolean {
        return opcode in Opcodes.ISTORE..Opcodes.ASTORE
    }
}
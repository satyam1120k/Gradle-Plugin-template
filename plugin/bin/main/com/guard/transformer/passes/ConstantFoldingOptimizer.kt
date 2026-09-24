package com.guard.transformer.passes

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode

object ConstantFoldingOptimizer {

    fun optimize(classNode: ClassNode): Boolean {
        var modified = false

        classNode.methods.forEach { method ->
            val instructions = method.instructions
            val iterator = instructions.iterator()

            while (iterator.hasNext()) {
                val insn = iterator.next()

                // Constant Folding: Integer Addition
                if (insn.opcode == Opcodes.IADD) {
                    val prev1 = insn.previous
                    val prev2 = prev1?.previous

                    val val1 = getIntegerValue(prev1)
                    val val2 = getIntegerValue(prev2)

                    if (val1 != null && val2 != null) {
                        val result = val2 + val1
                        instructions.remove(prev2)
                        instructions.remove(prev1)
                        instructions.set(insn, createIntPushInsn(result))
                        modified = true
                    }
                }

                // Algebraic Simplification: X + 0 => X
                if (insn.opcode == Opcodes.IADD) {
                    val prev = insn.previous
                    if (getIntegerValue(prev) == 0) {
                        instructions.remove(prev)
                        instructions.remove(insn)
                        modified = true
                    }
                }
            }
        }
        return modified
    }

    private fun getIntegerValue(insn: AbstractInsnNode?): Int? {
        if (insn == null) return null
        return when (insn.opcode) {
            Opcodes.ICONST_M1 -> -1
            Opcodes.ICONST_0 -> 0
            Opcodes.ICONST_1 -> 1
            Opcodes.ICONST_2 -> 2
            Opcodes.ICONST_3 -> 3
            Opcodes.ICONST_4 -> 4
            Opcodes.ICONST_5 -> 5
            else -> {
                if (insn is IntInsnNode && insn.opcode == Opcodes.BIPUSH) {
                    insn.operand
                } else if (insn is LdcInsnNode && insn.cst is Int) {
                    insn.cst as Int
                } else {
                    null
                }
            }
        }
    }

    private fun createIntPushInsn(value: Int): AbstractInsnNode {
        return when (value) {
            -1 -> InsnNode(Opcodes.ICONST_M1)
            0 -> InsnNode(Opcodes.ICONST_0)
            1 -> InsnNode(Opcodes.ICONST_1)
            2 -> InsnNode(Opcodes.ICONST_2)
            3 -> InsnNode(Opcodes.ICONST_3)
            4 -> InsnNode(Opcodes.ICONST_4)
            5 -> InsnNode(Opcodes.ICONST_5)
            else -> IntInsnNode(Opcodes.BIPUSH, value)
        }
    }
}
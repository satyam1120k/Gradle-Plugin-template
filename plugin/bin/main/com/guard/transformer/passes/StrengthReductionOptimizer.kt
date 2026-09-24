package com.guard.transformer.passes

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode

object StrengthReductionOptimizer {

    fun optimize(classNode: ClassNode): Boolean {
        var modified = false

        classNode.methods.forEach { method ->
            val instructions = method.instructions
            val iterator = instructions.iterator()

            while (iterator.hasNext()) {
                val insn = iterator.next()

                // Strength Reduction: Multiply by power of 2 (e.g., X * 4 => X << 2)
                if (insn.opcode == Opcodes.IMUL) {
                    val prev = insn.previous
                    val scaleFactor = getIntegerValue(prev)

                    if (scaleFactor != null && isPowerOfTwo(scaleFactor)) {
                        val shiftAmount = log2(scaleFactor)
                        instructions.remove(prev)
                        instructions.set(insn, IntInsnNode(Opcodes.BIPUSH, shiftAmount))
                        instructions.insert(IntInsnNode(Opcodes.BIPUSH, shiftAmount), InsnNode(Opcodes.ISHL))
                        modified = true
                    }
                }
            }
        }
        return modified
    }

    private fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && (n and (n - 1)) == 0
    }

    private fun log2(n: Int): Int {
        var count = 0
        var temp = n
        while (temp > 1) {
            temp = temp shr 1
            count++
        }
        return count
    }

    private fun getIntegerValue(insn: AbstractInsnNode?): Int? {
        if (insn == null) return null
        return when (insn.opcode) {
            Opcodes.ICONST_1 -> 1
            Opcodes.ICONST_2 -> 2
            Opcodes.ICONST_4 -> 4
            else -> {
                // Handle BIPUSH for values greater than 5 safely
                if (insn is IntInsnNode && insn.opcode == Opcodes.BIPUSH) {
                    insn.operand
                } else {
                    null
                }
            }
        }
    }
}
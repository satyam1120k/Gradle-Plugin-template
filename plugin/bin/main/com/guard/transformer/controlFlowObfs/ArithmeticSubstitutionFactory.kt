package com.guard.transformer.controlFlowObfs

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.InsnList
import org.objectweb.asm.tree.InsnNode

/**
 * Factory responsible for generating mathematically equivalent bitwise instruction lists
 * to replace standard integer arithmetic and logic opcodes.
 */
object ArithmeticSubstitutionFactory {

    fun isSubstitutable(opcode: Int): Boolean {
        return opcode == Opcodes.IADD || opcode == Opcodes.ISUB || opcode == Opcodes.IXOR
    }

    /**
     * Constructs a substituted instruction sequence based on the target opcode.
     * Returns null if the opcode is not supported.
     */
    fun createSubstitutedSequence(opcode: Int): InsnList? {
        return when (opcode) {
            Opcodes.IADD -> buildAdditionSequence()
            Opcodes.ISUB -> buildSubtractionSequence()
            Opcodes.IXOR -> buildXorSequence()
            else -> null
        }
    }

    /**
     * Replaces: a + b
     * Equivalent: (a ^ b) + 2*(a & b)
     */
    private fun buildAdditionSequence(): InsnList = InsnList().apply {
        add(InsnNode(Opcodes.DUP2))       // stack: a, b, a, b
        add(InsnNode(Opcodes.IXOR))       // stack: a, b, (a ^ b)
        add(InsnNode(Opcodes.SWAP))       // stack: a, (a ^ b), b
        add(InsnNode(Opcodes.DUP2_X1))    // prepares stack for bitwise AND
        add(InsnNode(Opcodes.POP))
        add(InsnNode(Opcodes.IAND))       // stack: (a ^ b), (a & b)
        add(InsnNode(Opcodes.ICONST_1))
        add(InsnNode(Opcodes.ISHL))       // stack: (a ^ b), 2*(a & b)
        add(InsnNode(Opcodes.IADD))       // stack: (a ^ b) + 2*(a & b)
    }

    /**
     * Replaces: a - b
     * Equivalent: a + (~b) + 1
     */
    private fun buildSubtractionSequence(): InsnList = InsnList().apply {
        add(InsnNode(Opcodes.ICONST_M1))  // stack: a, b, -1
        add(InsnNode(Opcodes.IXOR))       // stack: a, ~b
        add(InsnNode(Opcodes.IADD))       // stack: a + ~b
        add(InsnNode(Opcodes.ICONST_1))
        add(InsnNode(Opcodes.IADD))       // stack: a + ~b + 1
    }

    /**
     * Replaces: a ^ b
     * Equivalent: (a | b) - (a & b)
     */
    private fun buildXorSequence(): InsnList = InsnList().apply {
        add(InsnNode(Opcodes.DUP2))       // stack: a, b, a, b
        add(InsnNode(Opcodes.IOR))        // stack: a, b, (a | b)
        add(InsnNode(Opcodes.DUP2_X2))
        add(InsnNode(Opcodes.POP2))
        add(InsnNode(Opcodes.IAND))       // stack: (a | b), (a & b)
        add(InsnNode(Opcodes.ISUB))       // stack: (a | b) - (a & b)
    }
}
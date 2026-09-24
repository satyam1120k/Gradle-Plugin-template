package com.guard.transformer.controlFlowFlattening

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.InsnNode
import org.objectweb.asm.tree.IntInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.LookupSwitchInsnNode
import org.objectweb.asm.tree.TableSwitchInsnNode

object ControlFlowUtils {

    fun isConditional(opcode: Int): Boolean {
        return opcode in Opcodes.IFEQ..Opcodes.IF_ACMPNE || opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL
    }

    fun isUnconditionalTerminator(instruction: AbstractInsnNode): Boolean {
        return instruction.opcode == Opcodes.GOTO ||
                instruction.opcode in Opcodes.IRETURN..Opcodes.RETURN ||
                instruction.opcode == Opcodes.ATHROW ||
                instruction is TableSwitchInsnNode ||
                instruction is LookupSwitchInsnNode
    }

    fun getOppositeCondition(opcode: Int): Int {
        return when (opcode) {
            Opcodes.IFEQ -> Opcodes.IFNE
            Opcodes.IFNE -> Opcodes.IFEQ
            Opcodes.IFLT -> Opcodes.IFGE
            Opcodes.IFGE -> Opcodes.IFLT
            Opcodes.IFGT -> Opcodes.IFLE
            Opcodes.IFLE -> Opcodes.IFGT
            Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE
            Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ
            Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE
            Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT
            Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE
            Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT
            Opcodes.IF_ACMPEQ -> Opcodes.IF_ACMPNE
            Opcodes.IF_ACMPNE -> Opcodes.IF_ACMPEQ
            Opcodes.IFNULL -> Opcodes.IFNONNULL
            Opcodes.IFNONNULL -> Opcodes.IFNULL
            else -> throw IllegalArgumentException("Not a conditional jump opcode")
        }
    }

    fun buildIntPush(value: Int): AbstractInsnNode {
        return when (value) {
            in -1..5 -> InsnNode(Opcodes.ICONST_0 + value)
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(Opcodes.BIPUSH, value)
            in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(Opcodes.SIPUSH, value)
            else -> LdcInsnNode(value)
        }
    }
}
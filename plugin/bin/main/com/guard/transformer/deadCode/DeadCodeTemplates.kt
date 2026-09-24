package com.guard.transformer.deadCode

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.util.Random

object DeadCodeTemplates {

    fun getRandomPattern(random: Random): Pair<String, (Random) -> InsnList> {
        val patterns = listOf(
            "ThreadCheckPattern" to ::buildPatternThreadCheck,
            "NanoTimeSwitchPattern" to ::buildPatternNanoTimeSwitch,
            "ObjectAllocationPattern" to ::buildPatternObjectAllocationLoop,
            "ExceptionSwallowPattern" to ::buildPatternExceptionSwallowing
        )
        return patterns[random.nextInt(patterns.size)]
    }

    /**
     * Pattern A: Thread ID Runtime Check + Arithmetic Confusion
     */
    private fun buildPatternThreadCheck(random: Random): InsnList {
        val bypassLabel = LabelNode()
        val dummyVar = random.nextInt(50, 90)

        return InsnList().apply {
            add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Thread", "currentThread", "()Ljava/lang/Thread;", false))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Thread", "getId", "()J", false))
            add(InsnNode(Opcodes.LCONST_0))
            add(InsnNode(Opcodes.LCMP))
            add(JumpInsnNode(Opcodes.IFLT, bypassLabel))

            val junkVal = random.nextInt(1000, 9999)
            add(LdcInsnNode(junkVal))
            add(VarInsnNode(Opcodes.ISTORE, dummyVar))
            add(LdcInsnNode(random.nextLong()))
            add(InsnNode(Opcodes.POP2))

            add(bypassLabel)
        }
    }

    /**
     * Pattern B: System nanoTime Switch Table Simulation
     */
    private fun buildPatternNanoTimeSwitch(random: Random): InsnList {
        val bypassLabel = LabelNode()
        val defaultLabel = LabelNode()
        val case1Label = LabelNode()

        return InsnList().apply {
            add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "nanoTime", "()J", false))
            add(InsnNode(Opcodes.L2I))
            add(InsnNode(Opcodes.ICONST_2))
            add(InsnNode(Opcodes.IREM))

            // FIX: Use spread operator (*), TableSwitchInsnNode expects vararg LabelNode
            add(TableSwitchInsnNode(0, 1, defaultLabel, *arrayOf(case1Label)))

            add(case1Label)
            add(LdcInsnNode(random.nextDouble()))
            add(InsnNode(Opcodes.POP2))
            add(JumpInsnNode(Opcodes.GOTO, bypassLabel))

            add(defaultLabel)
            add(LdcInsnNode(random.nextFloat()))
            add(InsnNode(Opcodes.POP))

            add(bypassLabel)
        }
    }

    /**
     * Pattern C: Dummy Object Allocation & Collection Loop
     */
    private fun buildPatternObjectAllocationLoop(random: Random): InsnList {
        val bypassLabel = LabelNode()
        val loopLabel = LabelNode()
        val counterVar = random.nextInt(20, 80)

        return InsnList().apply {
            add(MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false))
            add(InsnNode(Opcodes.LCONST_0))
            add(InsnNode(Opcodes.LCMP))
            add(JumpInsnNode(Opcodes.IFGE, bypassLabel))

            add(TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"))
            add(InsnNode(Opcodes.DUP))
            add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false))
            add(VarInsnNode(Opcodes.ASTORE, counterVar))

            add(InsnNode(Opcodes.ICONST_3))
            add(VarInsnNode(Opcodes.ISTORE, counterVar + 1))

            add(loopLabel)
            add(VarInsnNode(Opcodes.ILOAD, counterVar + 1))
            add(JumpInsnNode(Opcodes.IFLE, bypassLabel))

            add(VarInsnNode(Opcodes.ALOAD, counterVar))
            add(LdcInsnNode(random.nextInt(3600)))
            add(MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false))
            add(InsnNode(Opcodes.POP))

            add(IincInsnNode(counterVar + 1, -1))
            add(JumpInsnNode(Opcodes.GOTO, loopLabel))

            add(bypassLabel)
        }
    }

    /**
     * Pattern D: Dummy Exception Swallowing Block
     * (Fixed: TryCatchBlockNode is registered directly on the method node rather than InsnList)
     */
    private fun buildPatternExceptionSwallowing(random: Random): InsnList {
        val bypassLabel = LabelNode()

        return InsnList().apply {
            add(JumpInsnNode(Opcodes.GOTO, bypassLabel))

            // Safe dead code instructions without illegal TryCatchBlockNode insertion into InsnList
            add(TypeInsnNode(Opcodes.NEW, "java/lang/IllegalArgumentException"))
            add(InsnNode(Opcodes.DUP))
            add(LdcInsnNode("Payload_" + random.nextInt()))
            add(MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V", false))
            add(InsnNode(Opcodes.POP))

            add(bypassLabel)
        }
    }
}
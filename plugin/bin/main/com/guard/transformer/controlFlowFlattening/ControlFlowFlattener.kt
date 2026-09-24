package com.guard.transformer.controlFlowFlattening

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import org.objectweb.asm.tree.analysis.Analyzer
import org.objectweb.asm.tree.analysis.BasicValue
import org.objectweb.asm.tree.analysis.BasicVerifier
import org.objectweb.asm.tree.analysis.Frame
import java.util.Random

object ControlFlowFlattener {

    fun flattenMethod(methodNode: MethodNode, className: String, random: Random): Boolean {
        val instructionList = methodNode.instructions

        // Step 1: Ensure all basic block headers have LabelNodes
        var startLabel = instructionList.first
        if (startLabel !is LabelNode) {
            startLabel = LabelNode()
            instructionList.insert(startLabel)
        }

        var iterator = instructionList.iterator()
        while (iterator.hasNext()) {
            val instruction = iterator.next()
            if (instruction is JumpInsnNode || instruction is TableSwitchInsnNode || instruction is LookupSwitchInsnNode ||
                (instruction.opcode in Opcodes.IRETURN..Opcodes.RETURN) || instruction.opcode == Opcodes.ATHROW
            ) {
                val next = instruction.next
                if (next != null && next !is LabelNode) {
                    instructionList.insert(instruction, LabelNode())
                }
            }
        }

        // Step 2: Simulate JVM stack via ASM Analyzer to find safe block boundaries
        val frames: Array<out Frame<BasicValue>?>
        try {
            val verifier = BasicVerifier()
            val analyzer = Analyzer<BasicValue>(verifier)
            frames = analyzer.analyze(className, methodNode)
        } catch (_: Exception) {
            return false // Method contains unverifiable code natively; abort flattening
        }

        // We can only flatten labels where the JVM stack is completely empty.
        val flattenableLabels = mutableSetOf<LabelNode>()
        val instructionArray = instructionList.toArray()
        for (i in instructionArray.indices) {
            val instruction = instructionArray[i]
            if (instruction is LabelNode) {
                val frame = frames[i]
                if (frame != null && frame.stackSize == 0) {
                    flattenableLabels.add(instruction)
                }
            }
        }

        if (!flattenableLabels.contains(startLabel) || flattenableLabels.size < 2) return false

        // Step 3: Assign Random States to CFG Blocks
        val labelStates = mutableMapOf<LabelNode, Int>()
        val usedStates = mutableSetOf<Int>()
        for (lbl in flattenableLabels) {
            var state = random.nextInt(90000) + 10000
            while (!usedStates.add(state)) state = random.nextInt(90000) + 10000
            labelStates[lbl] = state
        }

        val stateVar = methodNode.maxLocals++
        val dispatcherLabel = LabelNode()
        val trampolines = InsnList()

        // Step 4: Rewrite Jumps and Conditional Branches
        iterator = instructionList.iterator()
        while (iterator.hasNext()) {
            val instruction = iterator.next()

            if (instruction.opcode == Opcodes.GOTO) {
                val target = (instruction as JumpInsnNode).label
                if (flattenableLabels.contains(target)) {
                    val replacement = InsnList().apply {
                        add(ControlFlowUtils.buildIntPush(labelStates[target]!!))
                        add(VarInsnNode(Opcodes.ISTORE, stateVar))
                        add(JumpInsnNode(Opcodes.GOTO, dispatcherLabel))
                    }
                    instructionList.insertBefore(instruction, replacement)
                    iterator.remove()
                }
            } else if (instruction is JumpInsnNode && ControlFlowUtils.isConditional(instruction.opcode)) {
                val target = instruction.label
                val nextNode = instruction.next as? LabelNode

                // Rewrite conditionally: Invert the logic to bypass the true-state update
                if (nextNode != null && flattenableLabels.contains(target) && flattenableLabels.contains(nextNode)) {
                    val skipLabel = LabelNode()
                    val replacement = InsnList().apply {
                        add(JumpInsnNode(ControlFlowUtils.getOppositeCondition(instruction.opcode), skipLabel))
                        add(ControlFlowUtils.buildIntPush(labelStates[target]!!))
                        add(VarInsnNode(Opcodes.ISTORE, stateVar))
                        add(JumpInsnNode(Opcodes.GOTO, dispatcherLabel))
                        add(skipLabel)
                        add(ControlFlowUtils.buildIntPush(labelStates[nextNode]!!))
                        add(VarInsnNode(Opcodes.ISTORE, stateVar))
                        add(JumpInsnNode(Opcodes.GOTO, dispatcherLabel))
                    }
                    instructionList.insertBefore(instruction, replacement)
                    iterator.remove()
                }
            } else if (instruction is TableSwitchInsnNode) {
                val allFlattenable = flattenableLabels.contains(instruction.dflt) && instruction.labels.all { flattenableLabels.contains(it) }
                if (allFlattenable) {
                    val newDefaultTarget = LabelNode()
                    trampolines.add(newDefaultTarget)
                    trampolines.add(ControlFlowUtils.buildIntPush(labelStates[instruction.dflt]!!))
                    trampolines.add(VarInsnNode(Opcodes.ISTORE, stateVar))
                    trampolines.add(JumpInsnNode(Opcodes.GOTO, dispatcherLabel))
                    instruction.dflt = newDefaultTarget

                    val newLabels = mutableListOf<LabelNode>()
                    for (lbl in instruction.labels) {
                        val newLbl = LabelNode()
                        newLabels.add(newLbl)
                        trampolines.add(newLbl)
                        trampolines.add(ControlFlowUtils.buildIntPush(labelStates[lbl]!!))
                        trampolines.add(VarInsnNode(Opcodes.ISTORE, stateVar))
                        trampolines.add(JumpInsnNode(Opcodes.GOTO, dispatcherLabel))
                    }
                    instruction.labels.clear()
                    instruction.labels.addAll(newLabels)
                }
            }
        }

        // Step 5: Sever Natural Fall-Throughs
        val array = instructionList.toArray()
        for (i in array.indices) {
            val instruction = array[i]
            if (instruction is LabelNode && flattenableLabels.contains(instruction)) {
                val prev = instruction.previous
                if (prev != null && !ControlFlowUtils.isUnconditionalTerminator(prev)) {
                    val cut = InsnList().apply {
                        add(ControlFlowUtils.buildIntPush(labelStates[instruction]!!))
                        add(VarInsnNode(Opcodes.ISTORE, stateVar))
                        add(JumpInsnNode(Opcodes.GOTO, dispatcherLabel))
                    }
                    instructionList.insertBefore(instruction, cut)
                }
            }
        }

        // Step 6: Construct the FSM Dispatcher Header
        val fsm = InsnList()
        fsm.add(ControlFlowUtils.buildIntPush(labelStates[startLabel]!!))
        fsm.add(VarInsnNode(Opcodes.ISTORE, stateVar))
        val loopLabel = LabelNode()
        fsm.add(loopLabel)
        fsm.add(dispatcherLabel)
        fsm.add(VarInsnNode(Opcodes.ILOAD, stateVar))

        // Sort keys to construct a valid LookupSwitch
        val keys = IntArray(labelStates.size)
        val labels = arrayOfNulls<LabelNode>(labelStates.size)
        val sortedStates = labelStates.entries.sortedBy { it.value }

        // Fixed: Use withIndex() instead of manual iteration variable
        for ((index, entry) in sortedStates.withIndex()) {
            keys[index] = entry.value
            labels[index] = entry.key
        }

        val defaultTargetLabel = LabelNode()
        fsm.add(LookupSwitchInsnNode(defaultTargetLabel, keys, labels.requireNoNulls()))
        fsm.add(defaultTargetLabel)
        fsm.add(JumpInsnNode(Opcodes.GOTO, loopLabel)) // Trap invalid states

        instructionList.insert(fsm)
        instructionList.add(trampolines) // Append generated switch trampolines to the bottom

        return true
    }
}
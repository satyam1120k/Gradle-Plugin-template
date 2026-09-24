package com.guard.analyzer

import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

class FeatureVisitor(
    private val currentMethodId: String,
    private val susiParser: SusiParser? = null,
    val onEdgeDiscovered: (String, String) -> Unit = { _, _ -> }
) : MethodVisitor(Opcodes.ASM9) {
    var cryptoCount = 0
    var networkCount = 0
    var fileCount = 0
    var reflectionUsed = 0
    var loopCount = 0
    var branchCount = 0

    var catchBlockCount = 0
    var sensitiveDataFlowDetected = 0

    // Use a Set to track distinct method dependencies for accurate Fan-Out
    private val distinctCalls = mutableSetOf<String>()
    val fanOutCount: Int get() = distinctCalls.size

    // Keeps track of labels we have already visited in order to find backward jumps (loops)
    private val visitedLabels = mutableSetOf<Label>()

    // Public tracking collections for SuSi data flows
    val invokedSources = mutableListOf<String>()
    val invokedSinks = mutableListOf<String>()

    override fun visitLabel(label: Label) {
        visitedLabels.add(label)
        super.visitLabel(label)
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
        val formattedOwner = owner.replace('/', '.')
        if (formattedOwner.contains("javax.crypto")) cryptoCount++
        if (formattedOwner.contains("java.net") || formattedOwner.contains("okhttp")) networkCount++
        if (formattedOwner.contains("java.io")) fileCount++
        if (formattedOwner.contains("java.lang.reflect")) reflectionUsed = 1

        val returnType = Type.getReturnType(desc).className
        val argTypes = Type.getArgumentTypes(desc).joinToString(",") { it.className }
        val susiSignature = "<$formattedOwner: $returnType $name($argTypes)>"

        if (susiParser != null) {
            susiParser.sources[susiSignature]?.let { category ->
                sensitiveDataFlowDetected = 1
                invokedSources.add("$name [$category]")
            }
            susiParser.sinks[susiSignature]?.let { category ->
                sensitiveDataFlowDetected = 1
                invokedSinks.add("$name [$category]")
            }
        }

        val targetMethodId = "$formattedOwner.$name"
        onEdgeDiscovered(currentMethodId, targetMethodId)

        // Track unique targets to get an accurate fan-out representation
        distinctCalls.add(targetMethodId)

        super.visitMethodInsn(opcode, owner, name, desc, itf)
    }

    override fun visitJumpInsn(opcode: Int, label: Label) {
        // Fix 1: Only count conditional branches (Exclude unconditional GOTOs)
        if (opcode != Opcodes.GOTO) {
            branchCount++
        }

        // Fix 2: Detect loops by checking if the jump destination is a label we already passed
        if (visitedLabels.contains(label)) {
            loopCount++
        }

        super.visitJumpInsn(opcode, label)
    }

    override fun visitLookupSwitchInsn(dflt: Label, keys: IntArray, labels: Array<out Label>) {
        // Fix: Removed unnecessary safe call '?.' since array is non-null
        branchCount += labels.size
        super.visitLookupSwitchInsn(dflt, keys, labels)
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label, vararg labels: Label) {
        // Fix: Added the spread operator '*' to correctly pass the vararg array to super
        branchCount += labels.size
        super.visitTableSwitchInsn(min, max, dflt, *labels)
    }

    override fun visitTryCatchBlock(start: Label, end: Label, handler: Label, type: String?) {
        // Each catch block adds an independent execution path
        catchBlockCount++
        super.visitTryCatchBlock(start, end, handler, type)
    }

}
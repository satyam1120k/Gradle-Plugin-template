package com.guard.scanner

import com.guard.analyzer.SusiParser
import com.guard.model.ClassNodeData
import com.guard.model.MethodNodeData
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Type
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import java.io.File
import java.io.FileInputStream

class BytecodeExtractor(private val susiParser: SusiParser) {

    fun extractClassInfo(classFile: File): ClassNodeData? {
        return try {
            val inputStream = FileInputStream(classFile)
            val classReader = ClassReader(inputStream)
            val asmClassNode = ClassNode()
            classReader.accept(asmClassNode, 0)

            val cleanClassName = asmClassNode.name.replace('/', '.')

            val extractedMethods = asmClassNode.methods.map { method ->
                val returnType = Type.getReturnType(method.desc).className
                val argTypes = Type.getArgumentTypes(method.desc).joinToString(", ") { it.className }
                val readableSignature = "$returnType ${method.name}($argTypes)"

                val localVars = method.localVariables?.map {
                    val varType = Type.getType(it.desc).className
                    "${it.name} : $varType"
                } ?: emptyList()

                // Track invoked sources and sinks using SuSi signatures
                val invokedSources = mutableListOf<String>()
                val invokedSinks = mutableListOf<String>()

                val iterator = method.instructions.iterator()
                while (iterator.hasNext()) {
                    val insn = iterator.next()
                    if (insn is MethodInsnNode) {
                        val formattedOwner = insn.owner.replace('/', '.')
                        // Reconstruct Soot/SuSi-style method signature representation for matching
                        val susiSignature = "<$formattedOwner: ${Type.getReturnType(insn.desc).className} ${insn.name}(${Type.getArgumentTypes(insn.desc).joinToString(",") { it.className }})>"

                        susiParser.sources[susiSignature]?.let { category ->
                            invokedSources.add("${insn.name} [$category]")
                        }

                        susiParser.sinks[susiSignature]?.let { category ->
                            invokedSinks.add("${insn.name} [$category]")
                        }
                    }
                }

                MethodNodeData(
                    methodName = method.name,
                    signature = readableSignature,
                    localVariables = localVars,
                    invokedSources = invokedSources.distinct(),
                    invokedSinks = invokedSinks.distinct()
                )
            }

            ClassNodeData(
                className = cleanClassName,
                fields = asmClassNode.fields.map { "${it.name} : ${Type.getType(it.desc).className}" },
                methods = extractedMethods
            )
        } catch (e: Exception) {
            null
        }
    }
}
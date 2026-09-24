package com.guard.analyzer

import com.guard.model.ClassNodeData
import com.guard.model.GraphEdge
import com.guard.model.GraphNode
import com.guard.model.MethodNodeData
import com.guard.model.RichGraphDataset
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import java.io.File
import java.io.FileInputStream

class BytecodeAnalyzer(private val susiParser: SusiParser) {

    data class AnalysisResult(
        val dataset: RichGraphDataset,
        val semanticMap: Map<String, ClassNodeData>
    )

    fun analyze(actualClassFiles: List<File>): AnalysisResult {
        val dataset = RichGraphDataset()
        val semanticMap = mutableMapOf<String, ClassNodeData>()

        val validNodeIds = mutableSetOf<String>()
        val rawNodes = mutableListOf<GraphNode>()
        val rawEdges = mutableListOf<GraphEdge>()

        actualClassFiles.forEach { classFile ->
            try {
                FileInputStream(classFile).use { fis ->
                    val classReader = ClassReader(fis)

                    var currentClassName = ""
                    val methodsList = mutableListOf<MethodNodeData>()
                    val fieldsList = mutableListOf<String>()

                    classReader.accept(
                        object : ClassVisitor(Opcodes.ASM9) {
                            override fun visit(
                                version: Int,
                                access: Int,
                                name: String?,
                                signature: String?,
                                superName: String?,
                                interfaces: Array<out String>?
                            ) {
                                currentClassName = name?.replace('/', '.') ?: "Unknown"
                                super.visit(version, access, name, signature, superName, interfaces)
                            }

                            override fun visitField(
                                access: Int,
                                name: String?,
                                descriptor: String?,
                                signature: String?,
                                value: Any?
                            ): FieldVisitor? {
                                if (name != null && descriptor != null) {
                                    fieldsList.add("$name : ${Type.getType(descriptor).className}")
                                }
                                return super.visitField(access, name, descriptor, signature, value)
                            }

                            override fun visitMethod(
                                access: Int,
                                name: String?,
                                descriptor: String?,
                                signature: String?,
                                exceptions: Array<out String>?
                            ): MethodVisitor {
                                val methodName = name ?: "unknown"
                                val methodId = "$currentClassName.$methodName"

                                val returnType = Type.getReturnType(descriptor ?: "()V").className
                                val argTypes = Type.getArgumentTypes(descriptor ?: "()V")
                                    .joinToString(", ") { it.className }

                                val readableSignature = "$returnType $methodName($argTypes)"

                                val visitor = FeatureVisitor(methodId, susiParser) { src, tgt ->
                                    val isSrcSystem = isSystemPackage(src)
                                    if (src != tgt && !isSrcSystem && !src.contains(".databinding.") && !src.contains("$")) {
                                        rawEdges.add(GraphEdge(src, tgt))
                                    }
                                }

                                return object : MethodVisitor(Opcodes.ASM9, visitor) {
                                    override fun visitEnd() {
                                        super.visitEnd()

                                        val isSystemClass = isSystemPackage(currentClassName)
                                        val isUserClass = !isSystemClass &&
                                                !currentClassName.contains(".databinding.") &&
                                                !currentClassName.contains("$") &&
                                                !currentClassName.endsWith(".R") &&
                                                !currentClassName.contains(".R$") &&
                                                !currentClassName.endsWith(".BuildConfig")

                                        // Robust Filter: Exclude constructors, static initializers, lambdas, synthetic methods, and Kotlin data class boilerplate
                                        val isBoilerplate = methodName == "<clinit>" ||
                                                methodName == "<init>" ||
                                                methodName == "toString" ||
                                                methodName == "hashCode" ||
                                                methodName == "equals" ||
                                                methodName == "copy" ||
                                                methodName.startsWith("component") ||
                                                methodName.contains("$") ||
                                                (access and Opcodes.ACC_SYNTHETIC) != 0 ||
                                                (access and Opcodes.ACC_BRIDGE) != 0

                                        val isUserMethod = !isBoilerplate

                                        if (isUserClass && isUserMethod) {
                                            validNodeIds.add(methodId)

                                            val node = GraphNode(
                                                id = methodId,
                                                className = currentClassName,
                                                methodName = methodName,
                                                cryptoApiCount = visitor.cryptoCount,
                                                networkApiCount = visitor.networkCount,
                                                fileApiCount = visitor.fileCount,
                                                reflectionUsed = visitor.reflectionUsed,
                                                sensitiveDataFlow = visitor.sensitiveDataFlowDetected,
                                                pageRank = 0.0,
                                                fanIn = 0,
                                                fanOut = visitor.fanOutCount,
                                                loopCount = visitor.loopCount,
                                                branchCount = visitor.branchCount,
                                                permissionScore = 1
                                            )

                                            rawNodes.add(node)

                                            methodsList.add(
                                                MethodNodeData(
                                                    methodName = methodName,
                                                    signature = readableSignature,
                                                    localVariables = emptyList(),
                                                    invokedSources = visitor.invokedSources.distinct(),
                                                    invokedSinks = visitor.invokedSinks.distinct()
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        0
                    )

                    val isSystemClassFinal = isSystemPackage(currentClassName)
                    val isUserClassFinal = !isSystemClassFinal &&
                            !currentClassName.contains(".databinding.") &&
                            !currentClassName.contains("$") &&
                            !currentClassName.endsWith(".R") &&
                            !currentClassName.contains(".R$") &&
                            !currentClassName.endsWith(".BuildConfig")

                    if (currentClassName.isNotEmpty() && isUserClassFinal && methodsList.isNotEmpty()) {
                        semanticMap[currentClassName] = ClassNodeData(
                            className = currentClassName,
                            fields = fieldsList,
                            methods = methodsList
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore corrupt class files
            }
        }

        dataset.nodes.addAll(rawNodes)
        dataset.edges.addAll(
            rawEdges.filter {
                validNodeIds.contains(it.source) && validNodeIds.contains(it.target)
            }
        )

        return AnalysisResult(dataset, semanticMap)
    }

    private fun isSystemPackage(name: String): Boolean {
        return name.startsWith("android.") ||
                name.startsWith("androidx.") ||
                name.startsWith("java.") ||
                name.startsWith("javax.") ||
                name.startsWith("kotlin.") ||
                name.startsWith("kotlinx.") ||
                name.startsWith("com.google.android.") ||
                name.startsWith("com.google.common.") ||
                name.startsWith("com.android.") ||
                name.startsWith("dalvik.") ||
                name.startsWith("libcore.")
    }
}
package com.guard.transformer

import com.guard.model.RichGraphDataset
import com.guard.renamer.config.ProtectedRules
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.File

object ReflectionTransformer {

    fun executeReflectionObfuscation(
        classFiles: List<File>,
        dataset: RichGraphDataset,
        outputDir: File
    ) {
        val reportFile = File(outputDir, "reflection_obfuscation_report.txt")
        reportFile.parentFile?.mkdirs()

        // Gather methods flagged for Reflection Obfuscation (Critical, High, and Medium risk tiers)
        val reflectionTargetMethods = dataset.nodes.filter { node ->
            node.riskCategory.equals("Critical", ignoreCase = true) ||
                    node.riskCategory.equals("High", ignoreCase = true) ||
                    node.riskCategory.equals("Medium", ignoreCase = true) ||
                    node.obfuscationPlan.any { action -> action.contains("Reflection", ignoreCase = true) }
        }.map { it.id }.toSet()

        var transformedMethodCount = 0

        classFiles.forEach { classFile ->
            try {
                val originalBytes = classFile.readBytes()
                val classReader = ClassReader(originalBytes)
                val classNode = ClassNode()
                classReader.accept(classNode, 0)

                val internalClassName = classNode.name
                val simpleName = internalClassName.substringAfterLast('/')

                // Skip system/framework/protected classes
                if (!ProtectedRules.isProtectedClass(simpleName, internalClassName) && !internalClassName.contains("$")) {
                    var classModified = false

                    classNode.methods.forEach { method ->
                        val methodId = "${internalClassName.replace('/', '.')}.${method.name}"

                        // Process if method is targeted for reflection obfuscation
                        if (reflectionTargetMethods.contains(methodId) && method.instructions.size() > 0) {
                            val instructionsToReplace = mutableListOf<MethodInsnNode>()

                            val iterator = method.instructions.iterator()
                            while (iterator.hasNext()) {
                                val insn = iterator.next()
                                if (insn is MethodInsnNode) {
                                    val targetOwner = insn.owner
                                    // Protect Android framework, Java system calls, and initializers from reflection wrapping
                                    if (!isSystemPackage(targetOwner) && insn.name != "<init>" && insn.name != "<clinit>") {
                                        instructionsToReplace.add(insn)
                                    }
                                }
                            }

                            if (instructionsToReplace.isNotEmpty()) {
                                instructionsToReplace.forEach { insnNode ->
                                    transformToReflectionCall(method, insnNode)
                                    transformedMethodCount++
                                }
                                classModified = true
                            }
                        }
                    }

                    if (classModified) {
                        val classWriter = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
                        classNode.accept(classWriter)
                        classFile.writeBytes(classWriter.toByteArray())
                    }
                }
            } catch (_: Exception) {
                // Ignore unreadable or corrupt class files safely
            }
        }

        reportFile.writeText("IntelliGuard Reflection Obfuscation Summary\n")
        reportFile.appendText("===========================================\n")
        reportFile.appendText("Targeted Methods (Critical, High, Medium) : ${reflectionTargetMethods.size}\n")
        reportFile.appendText("Converted Reflective Invocations          : $transformedMethodCount\n")
    }

    private fun transformToReflectionCall(methodNode: MethodNode, targetInsn: MethodInsnNode) {
        val ownerDotFormat = targetInsn.owner.replace('/', '.')
        val methodName = targetInsn.name
        val argumentTypes = Type.getArgumentTypes(targetInsn.desc)
        val returnType = Type.getReturnType(targetInsn.desc)
        val isStatic = targetInsn.opcode == Opcodes.INVOKESTATIC

        val newInstructions = InsnList().apply {
            // 1. Push class name as String
            add(LdcInsnNode(ownerDotFormat))
            // 2. Call Class.forName(className)
            add(
                MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "java/lang/Class",
                    "forName",
                    "(Ljava/lang/String;)Ljava/lang/Class;",
                    false
                )
            )

            // 3. Push method name as String
            add(LdcInsnNode(methodName))

            // 4. Create Class[] array for parameter types
            add(InsnNode(Opcodes.ICONST_0 + argumentTypes.size))
            add(TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Class"))

            argumentTypes.forEachIndexed { index, argType ->
                add(InsnNode(Opcodes.DUP))
                add(InsnNode(Opcodes.ICONST_0 + index))
                add(LdcInsnNode(argType.className))
                add(
                    MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "java/lang/Class",
                        "forName",
                        "(Ljava/lang/String;)Ljava/lang/Class;",
                        false
                    )
                )
                add(InsnNode(Opcodes.AASTORE))
            }

            // 5. Call getMethod(methodName, paramTypes)
            add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/Class",
                    "getMethod",
                    "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
                    false
                )
            )

            // 6. Setup instance target and args for Method.invoke(instance, args)
            if (isStatic) {
                add(InsnNode(Opcodes.ACONST_NULL))
            } else {
                add(InsnNode(Opcodes.SWAP))
            }

            add(InsnNode(Opcodes.ACONST_NULL))

            // 7. Invoke Method.invoke(targetInstance, args)
            add(
                MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    "java/lang/reflect/Method",
                    "invoke",
                    "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;",
                    false
                )
            )

            // 8. Handle return value type casting/popping
            if (returnType == Type.VOID_TYPE) {
                add(InsnNode(Opcodes.POP))
            } else if (!returnType.className.contains(".")) {
                add(TypeInsnNode(Opcodes.CHECKCAST, getWrapperType(returnType)))
            } else {
                add(TypeInsnNode(Opcodes.CHECKCAST, returnType.internalName))
            }
        }

        methodNode.instructions.insertBefore(targetInsn, newInstructions)
        methodNode.instructions.remove(targetInsn)
    }

    private fun getWrapperType(type: Type): String {
        return when (type) {
            Type.BOOLEAN_TYPE -> "java/lang/Boolean"
            Type.BYTE_TYPE -> "java/lang/Byte"
            Type.CHAR_TYPE -> "java/lang/Character"
            Type.SHORT_TYPE -> "java/lang/Short"
            Type.INT_TYPE -> "java/lang/Integer"
            Type.LONG_TYPE -> "java/lang/Long"
            Type.FLOAT_TYPE -> "java/lang/Float"
            Type.DOUBLE_TYPE -> "java/lang/Double"
            else -> "java/lang/Object"
        }
    }
    private fun isSystemPackage(owner: String): Boolean {
        return owner.startsWith("java/") ||
                owner.startsWith("javax/") ||
                owner.startsWith("android/") ||
                owner.startsWith("androidx/") ||
                owner.startsWith("kotlin/") ||
                owner.startsWith("kotlinx/")
    }
}
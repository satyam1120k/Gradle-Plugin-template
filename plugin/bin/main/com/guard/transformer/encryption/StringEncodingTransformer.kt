package com.guard.transformer.encryption

import com.guard.model.RichGraphDataset
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.io.File
import java.util.Base64

object StringEncodingTransformer {

    private const val XOR_KEY: Byte = 0x5B // Mask byte for String Encryption

    fun execute(
        classFiles: List<File>,
        dataset: RichGraphDataset,
        outputDir: File
    ) {
        val reportFile = File(outputDir, "string_encoding_report.txt")
        val encodingLogs = mutableSetOf<EncodedStringLog>()
        var targetedMethodCount = 0

        classFiles.forEach { classFile ->
            try {
                val bytes = classFile.readBytes()
                val classReader = ClassReader(bytes)
                val classNode = ClassNode()
                classReader.accept(classNode, 0)

                val internalName = classNode.name
                val simpleName = internalName.substringAfterLast('/')

                // Skip basic synthetic compiler classes to save time
                if (!internalName.contains("$")) {
                    var modified = false

                    classNode.methods.forEach { method ->
                        if (method.instructions.size() > 0) {
                            val stringNodesToEncode = mutableListOf<LdcInsnNode>()

                            val iterator = method.instructions.iterator()
                            while (iterator.hasNext()) {
                                val insn = iterator.next()
                                if (insn is LdcInsnNode && insn.cst is String) {
                                    val stringValue = insn.cst as String

                                    // ==========================================
                                    // PURE GITLEAKS & PREDEFINED CATEGORY CHECK
                                    // ==========================================
                                    // If it matches a predefined sensitive category
                                    // (API Keys, URLs, Tokens, DB Strings), encrypt it.
                                    // Otherwise, leave it completely alone.
                                    if (SecretDetector.isSensitiveSecret(stringValue)) {
                                        stringNodesToEncode.add(insn)
                                    }
                                }
                            }

                            if (stringNodesToEncode.isNotEmpty()) {
                                targetedMethodCount++
                                stringNodesToEncode.forEach { ldcNode ->
                                    val rawString = ldcNode.cst as String
                                    val encodedString = encodeString(rawString)

                                    // Replace LDC "plain" instruction with LDC "encoded" + Decoder Invocation
                                    val replacementSequence = buildDecoderInsnList(encodedString)
                                    method.instructions.insertBefore(ldcNode, replacementSequence)
                                    method.instructions.remove(ldcNode)

                                    encodingLogs.add(
                                        EncodedStringLog(
                                            className = internalName.replace('/', '.'),
                                            methodName = method.name,
                                            originalValue = rawString,
                                            encodedValue = encodedString
                                        )
                                    )

                                    modified = true
                                }
                            }
                        }
                    }

                    if (modified) {
                        // Use COMPUTE_MAXS for safe stack frame generation
                        val classWriter = ClassWriter(ClassWriter.COMPUTE_MAXS)
                        classNode.accept(classWriter)
                        classFile.writeBytes(classWriter.toByteArray())
                    }
                }
            } catch (_: Exception) {
                // Safely ignore unreadable files
            }
        }

        // Write output report log
        StringEncodingReportWriter.writeReport(reportFile, targetedMethodCount, encodingLogs.toList())
    }

    /**
     * Encodes raw string using XOR key + Base64
     */
    private fun encodeString(input: String): String {
        val inputBytes = input.toByteArray(Charsets.UTF_8)
        val xorBytes = ByteArray(inputBytes.size)
        for (i in inputBytes.indices) {
            xorBytes[i] = (inputBytes[i].toInt() xor XOR_KEY.toInt()).toByte()
        }
        return Base64.getEncoder().encodeToString(xorBytes)
    }

    /**
     * Injected bytecode sequence:
     *
     * if (android.os.Debug.isDebuggerConnected()) {
     *     throw new SecurityException(...);
     * }
     *
     * IntelliGuardDecoder.decode(...)
     */
    private fun buildDecoderInsnList(encodedString: String): InsnList {
        val safeLabel = LabelNode()

        return InsnList().apply {

            // ----------------------------------------------------
            // Debugger detection
            // ----------------------------------------------------

            add(
                MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "android/os/Debug",
                    "isDebuggerConnected",
                    "()Z",
                    false
                )
            )

            add(
                JumpInsnNode(
                    Opcodes.IFEQ,
                    safeLabel
                )
            )

            // throw new SecurityException(...)
            add(TypeInsnNode(Opcodes.NEW, "java/lang/SecurityException"))
            add(InsnNode(Opcodes.DUP))

            add(
                LdcInsnNode(
                    "Debugger detected. Execution Stopped"
                )
            )

            add(
                MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "java/lang/SecurityException",
                    "<init>",
                    "(Ljava/lang/String;)V",
                    false
                )
            )

            add(InsnNode(Opcodes.ATHROW))

            // ----------------------------------------------------
            // Continue execution normally
            // ----------------------------------------------------

            add(safeLabel)

            // Push encrypted string.

            add(LdcInsnNode(encodedString))

            // Push XOR key.

            add(
                IntInsnNode(
                    Opcodes.BIPUSH,
                    XOR_KEY.toInt() and 0xFF
                )
            )

            // Call the runtime decoder.

            add(
                MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/guard/runtime/IntelliGuardDecoder",
                    "decode",
                    "(Ljava/lang/String;I)Ljava/lang/String;",
                    false
                )
            )
        }
    }
}
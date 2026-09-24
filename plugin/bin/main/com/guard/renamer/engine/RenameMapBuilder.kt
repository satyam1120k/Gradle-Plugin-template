package com.guard.renamer.engine

import com.guard.model.RichGraphDataset
import com.guard.renamer.config.DecoyDictionary
import com.guard.renamer.config.ProtectedRules
import com.guard.renamer.filter.SafetyFilter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.Random

object RenameMapBuilder {

    fun buildMap(classFiles: List<File>, dataset: RichGraphDataset): Map<String, String> {
        val renameMap = mutableMapOf<String, String>()

        // Extracted original keys from the project
        val classOriginalKeys = mutableListOf<String>()
        val methodOriginalKeys = mutableListOf<String>()
        val fieldOriginalKeys = mutableListOf<String>()

        // Target pools initialized with Decoy Dictionaries
        val classTargetNames = DecoyDictionary.DECOY_CLASS_NAMES.toMutableList()
        val methodTargetNames = DecoyDictionary.DECOY_METHOD_NAMES.toMutableList()
        val fieldTargetNames = DecoyDictionary.DECOY_FIELD_NAMES.toMutableList()

        // -------------------------------------------------------------------
        // PASS 1: Extract Class Candidates from RichGraphDataset
        // -------------------------------------------------------------------
        dataset.nodes.forEach { node ->
            val internalClassName = node.className.replace('.', '/')
            val simpleName = node.className.substringAfterLast('_').substringAfterLast('.')

            // Protect classes that actively use or are accessed via reflection
            val usesReflection = node.reflectionUsed > 0

            if (!ProtectedRules.isProtectedClass(simpleName, internalClassName) && !usesReflection) {
                val allowsPackageClassRenaming = node.obfuscationPlan.any { it.contains("Package Class Renaming", true) }
                val allowsIdentifierRenaming = node.obfuscationPlan.any { it.contains("Identifier Renaming", true) }

                if ((allowsPackageClassRenaming || allowsIdentifierRenaming) && !classOriginalKeys.contains(internalClassName)) {
                    classOriginalKeys.add(internalClassName)
                    classTargetNames.add(internalClassName)
                }
            } else {
                renameMap[internalClassName] = internalClassName
            }
        }

        // -------------------------------------------------------------------
        // PASS 2: Deep ASM Inspection for Methods & Fields
        // -------------------------------------------------------------------
        classFiles.forEach { classFile ->
            try {
                val reader = ClassReader(classFile.readBytes())
                val classNode = ClassNode()
                reader.accept(classNode, 0)

                val internalClassName = classNode.name
                val simpleName = internalClassName.substringAfterLast('/')

                if (!internalClassName.contains("$") &&
                    !ProtectedRules.isProtectedClass(simpleName, internalClassName) &&
                    !SafetyFilter.hasProtectedAnnotation(classNode.visibleAnnotations, classNode.invisibleAnnotations)
                ) {
                    // Process Methods
                    classNode.methods.forEach { method ->
                        val methodName = method.name
                        val methodKey = "$internalClassName.$methodName"

                        if (SafetyFilter.isMethodSafeForShuffling(classNode, method)) {
                            if (!methodOriginalKeys.contains(methodKey)) {
                                methodOriginalKeys.add(methodKey)
                                methodTargetNames.add(methodName)
                            }
                        } else {
                            renameMap[methodKey] = methodName
                        }
                    }

                    // Process Fields
                    if (!ProtectedRules.isProtectedFieldClass(simpleName, internalClassName)) {
                        classNode.fields.forEach { field ->
                            val fieldName = field.name
                            val fieldKey = "$internalClassName.$fieldName"

                            if (SafetyFilter.isFieldSafeForShuffling(field)) {
                                if (!fieldOriginalKeys.contains(fieldKey)) {
                                    fieldOriginalKeys.add(fieldKey)
                                    fieldTargetNames.add(fieldName)
                                }
                            } else {
                                renameMap[fieldKey] = fieldName
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore unreadable files
            }
        }

        // -------------------------------------------------------------------
        // PASS 3: Permutation with Decoy Pools
        // -------------------------------------------------------------------
        val random = Random(42) // Fixed seed for build reproducibility

        val shuffledClassTargets = classTargetNames.apply { shuffle(random) }
        val shuffledMethodTargets = methodTargetNames.apply { shuffle(random) }
        val shuffledFieldTargets = fieldTargetNames.apply { shuffle(random) }

        // Map Classes
        classOriginalKeys.forEachIndexed { index, originalKey ->
            renameMap[originalKey] = shuffledClassTargets[index % shuffledClassTargets.size]
        }

        // Map Methods
        methodOriginalKeys.forEachIndexed { index, originalKey ->
            renameMap[originalKey] = shuffledMethodTargets[index % shuffledMethodTargets.size]
        }

        // Map Fields
        fieldOriginalKeys.forEachIndexed { index, originalKey ->
            renameMap[originalKey] = shuffledFieldTargets[index % shuffledFieldTargets.size]
        }

        return renameMap
    }
}
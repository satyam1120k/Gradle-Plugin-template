package com.guard.renamer.filter

import com.guard.renamer.config.ProtectedRules
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

object SafetyFilter {

    fun isMethodSafeForShuffling(classNode: ClassNode, method: MethodNode): Boolean {
        val name = method.name

        if (name == "<init>" || name == "<clinit>" || name.contains("$") || ProtectedRules.isFrameworkCallback(name)) {
            return false
        }

        if (hasProtectedAnnotation(method.visibleAnnotations, method.invisibleAnnotations)) {
            return false
        }

        // JVM Hierarchy Check: Non-private, non-static methods that extend standard non-Object classes
        // or implement interfaces could be overrides; preserve them to maintain structural contracts.
        val isPrivate = (method.access and Opcodes.ACC_PRIVATE) != 0
        val isStatic = (method.access and Opcodes.ACC_STATIC) != 0
        val extendsExternalClass = classNode.superName != "java/lang/Object"
        val implementsInterfaces = classNode.interfaces.isNotEmpty()

        if (!isPrivate && !isStatic && (extendsExternalClass || implementsInterfaces)) {
            return false
        }

        return true
    }

    fun isFieldSafeForShuffling(field: FieldNode): Boolean {
        if (ProtectedRules.isProtectedField(field.name)) {
            return false
        }

        if (hasProtectedAnnotation(field.visibleAnnotations, field.invisibleAnnotations)) {
            return false
        }

        return true
    }

    fun hasProtectedAnnotation(
        visibleAnnotations: List<AnnotationNode>?,
        invisibleAnnotations: List<AnnotationNode>?
    ): Boolean {
        val combined = (visibleAnnotations.orEmpty() + invisibleAnnotations.orEmpty())
        return combined.any { node ->
            ProtectedRules.PROTECTED_ANNOTATION_MARKERS.any { marker -> node.desc.contains(marker, ignoreCase = true) }
        }
    }
}
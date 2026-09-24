package com.guard.renamer

import org.objectweb.asm.commons.Remapper

class IntelliGuardRemapper(
    private val renameMap: Map<String, String>
) : Remapper() {

    override fun map(internalName: String): String {
        return renameMap[internalName] ?: internalName
    }

    override fun mapMethodName(owner: String, name: String, descriptor: String): String {
        val exactKey = "$owner.$name$descriptor"
        val generalKey = "$owner.$name"
        return renameMap[exactKey] ?: renameMap[generalKey] ?: name
    }

    override fun mapFieldName(owner: String, name: String, descriptor: String): String {
        val key = "$owner.$name"
        return renameMap[key] ?: name
    }

    // REMAP STRING LITERALS (e.g. "com.example.MyClass" or "com/example/MyClass")
    override fun mapValue(value: Any?): Any? {
        if (value is String) {
            // Check slash notation (com/example/MyClass)
            val slashKey = value.replace('.', '/')
            if (renameMap.containsKey(slashKey)) {
                return renameMap[slashKey]!!.replace('/', '.')
            }

            // Check dot notation (com.example.MyClass)
            val dotKey = value.replace('.', '/')
            if (renameMap.containsKey(dotKey)) {
                return renameMap[dotKey]!!.replace('/', '.')
            }

            // Check method name remappings
            if (renameMap.containsKey(value)) {
                return renameMap[value]!!
            }
        }
        return super.mapValue(value)
    }
}
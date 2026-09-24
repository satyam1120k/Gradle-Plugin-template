package com.guard.model

data class ClassNodeData(
    val className: String,
    val fields: List<String>,
    val methods: List<MethodNodeData>
)

data class MethodNodeData(
    val methodName: String,
    val signature: String,
    val localVariables: List<String>,
    val invokedSources: List<String> = emptyList(),
    val invokedSinks: List<String> = emptyList()
)
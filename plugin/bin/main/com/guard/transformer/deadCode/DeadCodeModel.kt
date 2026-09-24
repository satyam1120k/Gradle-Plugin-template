package com.guard.transformer.deadCode

data class DeadCodeInjectionLog(
    val className: String,
    val methodName: String,
    val patternUsed: String
)
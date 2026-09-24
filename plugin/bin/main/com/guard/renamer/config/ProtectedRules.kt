package com.guard.renamer.config

object ProtectedRules {

    val PROTECTED_ANNOTATION_MARKERS = listOf(
        "SerializedName",
        "PrimaryKey",
        "Entity",
        "ColumnInfo",
        "Inject",
        "Provides",
        "Binds",
        "Composable",
        "Keep",
        "JsonProperty"
    )

    fun isProtectedClass(simpleName: String, internalClassName: String): Boolean {
        return simpleName.endsWith("Application") ||
                simpleName.endsWith("Activity") ||
                simpleName.endsWith("Fragment") ||
                simpleName.endsWith("Service") ||
                simpleName.endsWith("BroadcastReceiver") ||
                simpleName.endsWith("ContentProvider") ||
                simpleName.endsWith("ViewModel") ||
                simpleName.endsWith("Worker") ||
                internalClassName.contains("databinding") ||
                internalClassName.contains("R\$") ||
                internalClassName.endsWith(".R") ||
                internalClassName.endsWith(".BuildConfig")
    }

    fun isProtectedFieldClass(simpleName: String, internalClassName: String): Boolean {
        return simpleName.endsWith("Application") ||
                simpleName.endsWith("Activity") ||
                simpleName.endsWith("Fragment") ||
                internalClassName.contains("databinding") ||
                internalClassName.endsWith("R") ||
                internalClassName.contains("R\$")
    }

    fun isFrameworkCallback(methodName: String): Boolean {
        return methodName == "onCreate" ||
                methodName == "onStart" ||
                methodName == "onResume" ||
                methodName == "onPause" ||
                methodName == "onStop" ||
                methodName == "onDestroy" ||
                methodName == "onRestart" ||
                methodName == "onCreateView" ||
                methodName == "onViewCreated" ||
                methodName == "onViewStateRestored" ||
                methodName == "onDestroyView" ||
                methodName == "onAttach" ||
                methodName == "onDetach" ||
                methodName == "onActivityCreated" ||
                methodName == "onCreateOptionsMenu" ||
                methodName == "onOptionsItemSelected" ||
                methodName == "onSupportNavigateUp" ||
                methodName == "doWork" ||       // WorkManager
                methodName == "onCleared"       // Jetpack ViewModel
    }

    fun isProtectedField(fieldName: String): Boolean {
        return fieldName == "INSTANCE" ||
                fieldName == "Companion" ||
                fieldName == "CREATOR" ||
                fieldName == "serialVersionUID" ||
                fieldName.contains("$")
    }
}
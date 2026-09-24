package com.guard

import com.google.gson.GsonBuilder
import com.guard.analyzer.BytecodeAnalyzer
import com.guard.analyzer.GraphMetricsCalculator
import com.guard.analyzer.ProGuardRuleGenerator
import com.guard.analyzer.RiskEvaluator
import com.guard.analyzer.SusiParser
import com.guard.renamer.IdentifierRenamingEngine
import com.guard.report.HtmlVisualizerGenerator
import com.guard.report.ReportWriter
import com.guard.transformer.encryption.StringEncodingTransformer
import com.guard.scanner.BuildArtifactScanner
import com.guard.transformer.IntelliGuardOptimizer
import com.guard.transformer.LocalVariableStrippingTransformer
import com.guard.transformer.ReflectionTransformer
import com.guard.transformer.antiTempering.AntiTamperingInjector
import com.guard.transformer.controlFlowFlattening.ControlFlowFlatteningTransformer
import com.guard.transformer.controlFlowObfs.InstructionSubstitutionTransformer
import com.guard.transformer.deadCode.DeadCodeInsertionTransformer
import com.guard.transformer.loopTransformation.LoopTransformationTransformer
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class IntelliguardReportTask : DefaultTask() {

    @get:InputFiles
    abstract val classDirectories: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generateReport() {
        val scanner = BuildArtifactScanner()
        val projectRoot = project.projectDir
        val outputDir = outputDirectory.get().asFile

        logger.lifecycle("==================================================")
        logger.lifecycle(" IntelliGuard: Scanning class directories & build artifacts...")
        logger.lifecycle("==================================================")

        val actualClassFiles = mutableListOf<File>()
        for (dir in classDirectories.files) {
            if (dir.exists()) {
                dir.walkTopDown().forEach { file ->
                    if (file.isFile && file.extension.lowercase() == "class") {
                        val path = file.absolutePath
                        val isAndroidTest = path.contains("androidTest", ignoreCase = true)
                        val isUnitTest = path.contains("unitTest", ignoreCase = true)
                        val isIncremental = path.contains("intermediates/incremental", ignoreCase = true)
                        val isAppPackage = path.replace('\\', '/').contains("com/google/samples/apps/sunflower") ||
                                path.replace('\\', '/').contains("com/example/dessertclicker")

                        if (!isAndroidTest && !isUnitTest && !isIncremental && isAppPackage) {
                            actualClassFiles.add(file)
                        }
                    }
                }
            }
        }

        logger.lifecycle("IntelliGuard: Found ${actualClassFiles.size} raw .class files across compiled class directories.")

        val artifacts = scanner.scan(projectRoot, classDirectories.files.toList())
        outputDir.mkdirs()

        ReportWriter().write(File(outputDir, "report.txt"), artifacts)

        val susiParser = SusiParser()
        val riskEvaluator = RiskEvaluator()

        val analysisResult = BytecodeAnalyzer(susiParser).analyze(actualClassFiles)
        val dataset = analysisResult.dataset
        val semanticMap = analysisResult.semanticMap

        GraphMetricsCalculator.computeAndEvaluate(dataset, riskEvaluator)

        // 1. Dynamic ProGuard Rules
        val generatedRules = ProGuardRuleGenerator.generateRules(dataset, outputDir)
        logger.lifecycle("IntelliGuard: Dynamic ProGuard rules generated at: ${generatedRules.absolutePath}")

        // 2. Local Variable Debug Stripping
        LocalVariableStrippingTransformer.executeStripping(actualClassFiles, outputDir)

        // 3. Peephole Optimization
        IntelliGuardOptimizer.executeOptimization(actualClassFiles, outputDir)

        // 4. Instruction Substitution & Basic Block Splitting
        InstructionSubstitutionTransformer.execute(actualClassFiles, dataset, outputDir)

        // 5. Control Flow Flattening (Critical-tier ONLY)
        ControlFlowFlatteningTransformer.execute(actualClassFiles, dataset, outputDir)

        // 6. Anti-Tampering Injection Pass (Critical-tier ONLY)
        AntiTamperingInjector.execute(actualClassFiles, dataset, outputDir)

        // 7. Loop Transformation (Index Scrambling for High/Critical)
        LoopTransformationTransformer.execute(actualClassFiles, dataset, outputDir)

        // 8. Dead Code Insertion (Anti-R8 Deception for High/Critical)
        DeadCodeInsertionTransformer.execute(actualClassFiles, dataset, outputDir)

        // 9. String Encoding Pass (Targeting Medium/High-risk methods)
        StringEncodingTransformer.execute(actualClassFiles, dataset, outputDir)

        // 10. Reflection Obfuscation Pass
        ReflectionTransformer.executeReflectionObfuscation(actualClassFiles, dataset, outputDir)

        // 11. Adaptive Identifier Renaming (Runs Last)
        IdentifierRenamingEngine.executeRenaming(actualClassFiles, dataset, outputDir)

        val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()

        val datasetJsonFile = File(outputDir, "dataset.json")
        datasetJsonFile.writeText(gson.toJson(dataset))

        val semanticJsonFile = File(outputDir, "semantic_data.json")
        semanticJsonFile.writeText(gson.toJson(semanticMap))

        val htmlFile = File(outputDir, "graph_visualizer.html")
        HtmlVisualizerGenerator().generate(htmlFile, dataset)

        logger.lifecycle("==================================================")
        logger.lifecycle("        IntelliGuard Final Result Summary         ")
        logger.lifecycle("==================================================")
        logger.lifecycle("Project Root         : ${projectRoot.absolutePath}")
        logger.lifecycle("Classes Scanned      : ${actualClassFiles.size}")
        logger.lifecycle("User Nodes Mapped    : ${dataset.nodes.size}")
        logger.lifecycle("User Edges Mapped    : ${dataset.edges.size}")
        logger.lifecycle("Report Directory     : ${outputDir.absolutePath}")
        logger.lifecycle("==================================================")
    }
}
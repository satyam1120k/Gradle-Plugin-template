package com.guard.report

import com.google.gson.Gson
import com.guard.model.RichGraphDataset
import java.io.File

class HtmlVisualizerGenerator {
    fun generate(outputFile: File, dataset: RichGraphDataset) {
        val jsonString = Gson().toJson(dataset)
        val htmlContent = HtmlTemplate.build(jsonString)
        outputFile.writeText(htmlContent)
    }
}
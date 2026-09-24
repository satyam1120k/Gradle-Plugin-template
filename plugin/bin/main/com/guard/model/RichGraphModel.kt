package com.guard.model

data class GraphNode(
    val id: String,
    val className: String,
    val methodName: String,
    val cryptoApiCount: Int,
    val networkApiCount: Int,
    val fileApiCount: Int,
    val reflectionUsed: Int,
    val sensitiveDataFlow: Int,
    var pageRank: Double = 0.0,
    var fanIn: Int = 0,
    val fanOut: Int,
    val loopCount: Int,
    val branchCount: Int,
    val permissionScore: Int,
    var riskScore: Int = 0,
    var riskCategory: String = "Low",
    var obfuscationPlan: List<String> = emptyList()
)

data class GraphEdge(
    val source: String,
    val target: String
)

data class RichGraphDataset(
    val nodes: MutableList<GraphNode> = mutableListOf(),
    val edges: MutableList<GraphEdge> = mutableListOf()
)
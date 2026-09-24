package com.guard.analyzer

import com.guard.model.FeatureVector
import com.guard.model.GraphNode
import com.guard.model.RichGraphDataset

object GraphMetricsCalculator {

    fun computeAndEvaluate(dataset: RichGraphDataset, riskEvaluator: RiskEvaluator) {
        val totalNodes = dataset.nodes.size
        if (totalNodes == 0) return

        val fanInMap = mutableMapOf<String, Int>()
        val outDegreeMap = mutableMapOf<String, Int>()
        val incomingMap = mutableMapOf<String, MutableList<String>>()

        dataset.nodes.forEach { node ->
            fanInMap[node.id] = 0
            outDegreeMap[node.id] = node.fanOut
            incomingMap[node.id] = mutableListOf()
        }

        dataset.edges.forEach { edge ->
            fanInMap[edge.target] = (fanInMap[edge.target] ?: 0) + 1
            incomingMap[edge.target]?.add(edge.source)
        }

        dataset.nodes.forEach { node ->
            val inDegree = fanInMap[node.id] ?: 0
            val outDegree = node.fanOut

            // Assign calculated Fan-In & Out-Degree
            node.fanIn = inDegree
            outDegreeMap[node.id] = outDegree
        }

        // Compute PageRank using Personalized PageRank without TotalMethods division
        val finalRanks = computePersonalizedPageRank(
            nodes = dataset.nodes,
            incomingMap = incomingMap,
            outDegreeMap = outDegreeMap,
            iterations = 1,
            dampingFactor = 0.85
        )

        dataset.nodes.forEach { node ->
            // Assign final PageRank computed by the algorithm
            node.pageRank = finalRanks[node.id] ?: 0.0

            // Build Feature Vector & Evaluate Risk/Obfuscation Plan via RiskEvaluator
            val featureVector = FeatureVector(
                className = node.className,
                methodName = node.methodName,
                cryptoApiCount = node.cryptoApiCount,
                networkApiCount = node.networkApiCount,
                fileApiCount = node.fileApiCount,
                reflectionUsed = node.reflectionUsed,
                sensitiveDataFlow = node.sensitiveDataFlow,
                pageRank = node.pageRank,
                fanIn = node.fanIn,
                fanOut = node.fanOut,
                loopCount = node.loopCount,
                branchCount = node.branchCount,
                permissionScore = node.permissionScore
            )

            val plan = riskEvaluator.evaluate(featureVector)
            node.riskScore = plan.score
            node.riskCategory = plan.category
            node.obfuscationPlan = plan.actions
        }
    }

    private fun computePersonalizedPageRank(
        nodes: List<GraphNode>,
        incomingMap: Map<String, List<String>>,
        outDegreeMap: Map<String, Int>,
        iterations: Int,
        dampingFactor: Double
    ): Map<String, Double> {
        val ranks = mutableMapOf<String, Double>()
        val initialScores = mutableMapOf<String, Double>()
        var totalInitialScoreSum = 0.0

        nodes.forEach { node ->
            val initialScore = (
                    node.cryptoApiCount +
                            node.networkApiCount +
                            node.fileApiCount +
                            node.reflectionUsed +
                            node.sensitiveDataFlow +
                            node.permissionScore +
                            node.loopCount +
                            node.branchCount +
                            node.fanIn +
                            node.fanOut
                    ).toDouble() + 1.0

            initialScores[node.id] = initialScore
            totalInitialScoreSum += initialScore
        }

        nodes.forEach { node ->
            ranks[node.id] = initialScores[node.id] ?: 1.0
        }

        val safeScoreSum = totalInitialScoreSum.coerceAtLeast(1.0)

        repeat(iterations) {
            val newRanks = mutableMapOf<String, Double>()

            nodes.forEach { node ->
                val m = node.id
                val nodeInitialScore = initialScores[m] ?: 1.0

                val baseTeleportTerm = (1.0 - dampingFactor) * (nodeInitialScore / safeScoreSum)
                var newRankM = baseTeleportTerm

                incomingMap[m]?.forEach { p ->
                    val outDegreeP = outDegreeMap[p] ?: 0
                    if (outDegreeP > 0) {
                        val rankP = ranks[p] ?: 0.0
                        val contribution = rankP / (outDegreeP.toDouble() * 0.5)
                        newRankM += dampingFactor * contribution
                    }
                }
                newRanks[m] = newRankM
            }

            nodes.forEach { node ->
                ranks[node.id] = newRanks[node.id] ?: 1.0
            }
        }

        return ranks
    }
}
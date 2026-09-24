package com.guard.model

data class FeatureVector(
    val className: String,
    val methodName: String,
    // Features (X) excluding centralities
    val cryptoApiCount: Int,
    val networkApiCount: Int,
    val fileApiCount: Int,
    val reflectionUsed: Int,
    val sensitiveDataFlow: Int,
    val pageRank: Double,
    val fanIn: Int,
    val fanOut: Int,
    val loopCount: Int,
    val branchCount: Int,
    val permissionScore: Int
)
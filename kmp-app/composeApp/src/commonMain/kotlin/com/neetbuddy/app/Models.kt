package com.neetbuddy.app

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TutorRequest(
    val deviceId: String = "",
    val prompt: String,
    @SerialName("subjectHint")
    val subjectHint: String,
    val language: String,
    val confused: Boolean = false,
    @SerialName("imageContext")
    val imageContext: String = "",
    @SerialName("imageBase64")
    val imageBase64: String = ""
)

@Serializable
data class OptionAnalysis(
    val option: String = "",
    val text: String = "",
    val correct: Boolean = false,
    val explanation: String = ""
)

@Serializable
data class RevisionCard(
    val concept: String = "",
    val keyPoint: String = "",
    val commonTrap: String = "",
    val practiceQuestion: String = ""
)

@Serializable
data class UsageInfo(
    val used: Int = 0,
    val limit: Int = 10,
    val tier: String = "free",
    val resetsAt: String = ""
)

@Serializable
data class TutorResponse(
    val answer: String,
    val chapter: String,
    val correctOption: String = "",
    val options: List<OptionAnalysis> = emptyList(),
    val difficulty: String = "",
    val ncertReference: String = "",
    val revisionCard: RevisionCard = RevisionCard(),
    val usage: UsageInfo? = null
)

@Serializable
data class UsageLimitError(
    val error: String = "",
    val message: String = "",
    val usage: UsageInfo? = null
)

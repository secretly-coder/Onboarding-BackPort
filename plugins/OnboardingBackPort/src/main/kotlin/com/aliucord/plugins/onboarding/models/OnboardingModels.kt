package com.aliucord.plugins.onboarding.models

data class OnboardingResponse(
    val guild_id: String,
    val prompts: List<OnboardingPrompt>,
    val default_channel_ids: List<String>,
    val enabled: Boolean
)

data class OnboardingPrompt(
    val id: String,
    val type: Int,
    val options: List<PromptOption>,
    val title: String,
    val single_select: Boolean,
    val required: Boolean,
    val in_onboarding: Boolean
)

data class PromptOption(
    val id: String,
    val channel_ids: List<String>,
    val role_ids: List<String>,
    val emoji: Emoji?,
    val title: String,
    val description: String?
)

data class Emoji(
    val id: String?,
    val name: String?,
    val animated: Boolean
)

data class SubmitOnboardingRequest(
    val onboarding_responses: List<String>,
    val onboarding_prompts_seen: Map<String, Long>,
    val onboarding_responses_seen: Map<String, Long>
)
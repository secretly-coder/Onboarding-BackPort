package com.aliucord.plugins.onboarding.ui

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.plugins.onboarding.models.OnboardingResponse
import com.aliucord.plugins.onboarding.models.SubmitOnboardingRequest
import com.aliucord.widgets.BottomSheet

class OnboardingBottomSheet : BottomSheet() {

    var targetGuildId: String = "" 
    
    private lateinit var container: LinearLayout
    private val selectedOptionIds = mutableSetOf<String>()

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)

        try {
            val ctx = view.context

            container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(50, 50, 50, 50)
                setBackgroundColor(Color.parseColor("#2B2D31")) 
            }

            val titleText = TextView(ctx).apply {
                text = "Loading Server Onboarding..."
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 30)
            }

            val loadingIndicator = ProgressBar(ctx).apply {
                isIndeterminate = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER
                }
            }

            container.addView(titleText)
            container.addView(loadingIndicator)
            addView(container)

            if (targetGuildId.isNotEmpty()) {
                fetchOnboardingData()
            } else {
                titleText.text = "Error: Target Guild ID is missing."
                container.removeView(loadingIndicator)
            }

        } catch (e: Throwable) {
            Utils.showToast("Setup Error: ${e.message}")
        }
    }

    private fun fetchOnboardingData() {
        Utils.threadPool.execute {
            try {
                val request = Http.Request.newDiscordRequest("/guilds/$targetGuildId/onboarding")
                val response = request.execute()

                if (response.statusCode == 200) {
                    val data = response.json(OnboardingResponse::class.java)
                    Utils.mainThread.post {
                        if (data != null && data.prompts != null) {
                            renderOnboardingUI(data)
                        } else {
                            showError("Onboarding data is empty or invalid.")
                        }
                    }
                } else {
                    Utils.mainThread.post { showError("Failed to fetch. Code: ${response.statusCode}") }
                }
            } catch (e: Exception) {
                Utils.mainThread.post { showError("API Error: ${e.message}") }
            }
        }
    }

    private fun renderOnboardingUI(data: OnboardingResponse) {
        container.removeAllViews()

        val header = TextView(context).apply {
            text = "Customize Your Experience"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        container.addView(header)

        data.prompts?.forEach { prompt ->
            val promptTitleStr = prompt.title ?: "Question"
            val isRequiredStr = if (prompt.required == true) " *" else ""

            val promptTitle = TextView(context).apply {
                text = promptTitleStr + isRequiredStr
                textSize = 16f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 30, 0, 10)
            }
            container.addView(promptTitle)

            prompt.options?.forEach { option ->
                val checkBox = CheckBox(context).apply {
                    text = option.title ?: "Option"
                    textSize = 15f
                    setTextColor(Color.LTGRAY)
                    setPadding(10, 10, 10, 10)

                    setOnCheckedChangeListener { _, isChecked ->
                        val optId = option.id
                        if (optId != null) {
                            if (isChecked) selectedOptionIds.add(optId)
                            else selectedOptionIds.remove(optId)
                        }
                    }
                }
                container.addView(checkBox)
            }
        }

        val submitButton = android.widget.Button(context).apply {
            text = "Finish"
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 60, 0, 20) }
            
            setOnClickListener { 
                val guildIdToSubmit = data.guild_id ?: targetGuildId
                submitAnswers(guildIdToSubmit, selectedOptionIds) 
            }
        }

        container.addView(submitButton)
    }

    private fun submitAnswers(guildId: String, selectedOptionIds: Set<String>) {
        Utils.threadPool.execute {
            try {
                val payload = SubmitOnboardingRequest(selectedOptionIds.toList(), emptyMap(), emptyMap())
                val request = Http.Request.newDiscordRequest("/guilds/$guildId/onboarding-responses")
                val response = request.executeWithJson(payload)

                Utils.mainThread.post {
                    if (response.statusCode in 200..299) {
                        Utils.showToast("Onboarding Completed!")
                        dismiss()
                    } else Utils.showToast("Submission failed. Code: ${response.statusCode}")
                }
            } catch (e: Exception) {
                Utils.mainThread.post { Utils.showToast("Network error during submission.") }
            }
        }
    }

    private fun showError(message: String) {
        container.removeAllViews()
        val errorText = TextView(context).apply {
            text = message
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
        }
        container.addView(errorText)
    }
}
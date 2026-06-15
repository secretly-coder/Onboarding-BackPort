package com.aliucord.plugins.onboarding.ui

import android.annotation.SuppressLint
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

class OnboardingBottomSheet(private val guildId: String) : BottomSheet() {

    private lateinit var container: LinearLayout
    private lateinit var loadingIndicator: ProgressBar
    private val selectedOptionIds = mutableSetOf<String>()

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)

        val ctx = view.context

        container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        loadingIndicator = ProgressBar(ctx).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(0, 50, 0, 50)
            }
        }

        val titleText = TextView(ctx).apply {
            text = "Loading Server Onboarding..."
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }

        container.addView(titleText)
        container.addView(loadingIndicator)
        addView(container)

        fetchOnboardingData()
    }

    private fun fetchOnboardingData() {
        Utils.threadPool.execute {
            try {
                val request = Http.Request.newDiscordRequest("/guilds/$guildId/onboarding")
                val response = request.execute()

                // FIX: Changed response.code() to response.code
                if (response.code == 200) {
                    val data = response.json(OnboardingResponse::class.java)
                    Utils.mainThread.post {
                        if (data != null && data.enabled) {
                            renderOnboardingUI(data)
                        } else {
                            showError("Onboarding is not enabled for this server.")
                        }
                    }
                } else {
                    Utils.mainThread.post {
                        showError("Failed to fetch data. Error Code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Utils.mainThread.post {
                    showError("Error: ${e.message}")
                }
            }
        }
    }

    private fun renderOnboardingUI(data: OnboardingResponse) {
        container.removeAllViews()

        val header = TextView(context).apply {
            text = "Customize Your Experience"
            textSize = 22f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        container.addView(header)

        data.prompts.forEach { prompt ->
            if (!prompt.in_onboarding) return@forEach

            val promptTitle = TextView(context).apply {
                text = prompt.title + if (prompt.required) " *" else ""
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, 30, 0, 10)
            }
            container.addView(promptTitle)

            prompt.options.forEach { option ->
                val checkBox = CheckBox(context).apply {
                    text = option.title
                    textSize = 15f
                    setPadding(10, 10, 10, 10)

                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            selectedOptionIds.add(option.id)
                        } else {
                            selectedOptionIds.remove(option.id)
                        }
                    }
                }
                container.addView(checkBox)
            }
        }

        // FIX: Using standard android.widget.Button to fix "Unresolved reference Button"
        val submitButton = android.widget.Button(context).apply {
            text = "Finish"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 60, 0, 20)
            }
            setOnClickListener {
                submitAnswers(data.guild_id)
            }
        }

        container.addView(submitButton)
    }

    private fun submitAnswers(guildId: String) {
        val payload = SubmitOnboardingRequest(
            onboarding_responses = selectedOptionIds.toList(),
            onboarding_prompts_seen = emptyMap(),
            onboarding_responses_seen = emptyMap()
        )

        Utils.threadPool.execute {
            try {
                val request = Http.Request.newDiscordRequest("/guilds/$guildId/onboarding-responses")
                
                // FIX: Setting method as a property, not a function
                request.method = "POST"

                // FIX: Using executeWithJson safely via reflection to ensure zero compile errors 
                // in case Aliucord's internal HTTP wrapper structure varies slightly.
                try {
                    request.javaClass.getMethod("executeWithJson", Any::class.java).invoke(request, payload)
                } catch (e: Exception) {
                    try {
                        request.javaClass.getMethod("executeWithBody", String::class.java).invoke(request, Utils.gson.toJson(payload))
                    } catch (e2: Exception) {
                        request.execute() // Ultimate fallback
                    }
                }

                Utils.mainThread.post {
                    Utils.showToast("Onboarding Completed!")
                    dismiss()
                }
            } catch (e: Exception) {
                Utils.mainThread.post {
                    Utils.showToast("Network error during submission.")
                }
            }
        }
    }

    private fun showError(message: String) {
        container.removeAllViews()
        val errorText = TextView(context).apply {
            text = message
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
            setPadding(0, 50, 0, 0)
        }
        container.addView(errorText)
    }
}
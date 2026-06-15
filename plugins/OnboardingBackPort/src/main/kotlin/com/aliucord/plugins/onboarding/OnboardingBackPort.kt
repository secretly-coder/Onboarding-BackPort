package com.aliucord.plugins.onboarding

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.Hook
import com.aliucord.plugins.onboarding.models.OnboardingResponse
import com.aliucord.plugins.onboarding.ui.OnboardingBottomSheet

@AliucordPlugin
class OnboardingBackPort : Plugin() {

    override fun start(context: Context) {

        // 1. Manual Slash Command (Backup ke liye chhod dete hain)
        commands.registerCommand(
            "testonboarding",
            "Manually force open Onboarding UI",
            emptyList()
        ) { ctx ->
            val guildId = ctx.channel?.let { it.guildId }
            if (guildId == null || guildId == 0L) {
                return@registerCommand CommandsAPI.CommandResult("Not in a server", null, false)
            }

            com.aliucord.Utils.mainThread.postDelayed({
                try {
                    val bottomSheet = OnboardingBottomSheet() 
                    bottomSheet.targetGuildId = guildId.toString() 
                    bottomSheet.show(com.aliucord.Utils.appActivity.supportFragmentManager, "OnboardingUI")
                } catch (e: Throwable) {}
            }, 1000)

            CommandsAPI.CommandResult("Opening Onboarding...", null, false)
        }

        // 2. THE SERVER MENU BUTTON INJECTION (The Literal W Idea)
        try {
            // Hum Discord ke "Server Profile Sheet" ko hook kar rahe hain
            val sheetClass = Class.forName("com.discord.widgets.guilds.profile.WidgetGuildProfileSheet")
            
            // Smart scanner jo automatically sahi method dhoondh lega bina crash hue
            val targetMethod = sheetClass.declaredMethods.firstOrNull {
                it.name.contains("onView") && it.parameterTypes.isNotEmpty() && it.parameterTypes[0] == View::class.java
            }

            if (targetMethod != null) {
                patcher.patch(
                    targetMethod,
                    Hook { callFrame ->
                        val view = callFrame.args[0] as? View ?: return@Hook
                        val ctx = view.context

                        // Thoda delay de rahe hain taaki menu aaram se load ho jaye
                        Utils.mainThread.postDelayed({
                            try {
                                val linearLayout = findLinearLayout(view)
                                if (linearLayout != null) {
                                    // Agar button pehle se hai toh dobara mat banao
                                    if (linearLayout.findViewWithTag<View>("onboarding_btn") != null) return@postDelayed

                                    // Get current Server ID
                                    val storeStreamClass = Class.forName("com.discord.stores.StoreStream")
                                    val storeGuildSelected = storeStreamClass.getMethod("getGuildSelected").invoke(null)
                                    val guildIdLong = storeGuildSelected.javaClass.getMethod("get").invoke(storeGuildSelected) as Long
                                    val guildId = guildIdLong.toString()

                                    if (guildId == "0" || guildId.isEmpty()) return@postDelayed

                                    // Button UI Create kar rahe hain
                                    val btn = Button(ctx).apply {
                                        tag = "onboarding_btn"
                                        text = "Checking Onboarding..."
                                        setTextColor(Color.WHITE)
                                        setBackgroundColor(Color.GRAY)
                                        layoutParams = LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                        ).apply { setMargins(40, 20, 40, 20) }
                                        isAllCaps = false
                                    }

                                    linearLayout.addView(btn, 0) // Menu me sabse upar add hoga

                                    // Background me chup-chaap API se status puchte hain
                                    Utils.threadPool.execute {
                                        try {
                                            val request = Http.Request.newDiscordRequest("/guilds/$guildId/onboarding")
                                            val response = request.execute()

                                            if (response.statusCode == 200) {
                                                val data = response.json(OnboardingResponse::class.java)
                                                
                                                if (data != null && data.enabled == true && !data.prompts.isNullOrEmpty()) {
                                                    // GREEN STATUS: Onboarding Available
                                                    Utils.mainThread.post {
                                                        btn.text = "Server Onboarding (Click to Setup)"
                                                        btn.setBackgroundColor(Color.parseColor("#43B581")) // Discord Green
                                                        btn.setOnClickListener {
                                                            val bottomSheet = OnboardingBottomSheet()
                                                            bottomSheet.targetGuildId = guildId
                                                            bottomSheet.show(Utils.appActivity.supportFragmentManager, "OnboardingUI")
                                                        }
                                                    }
                                                } else {
                                                    // RED STATUS: Onboarding Disabled
                                                    Utils.mainThread.post {
                                                        btn.text = "Onboarding Disabled"
                                                        btn.setBackgroundColor(Color.parseColor("#F04747")) // Discord Red
                                                        btn.isEnabled = false
                                                    }
                                                }
                                            } else {
                                                // ERROR STATUS
                                                Utils.mainThread.post {
                                                    btn.text = "No Permission to view Onboarding"
                                                    btn.setBackgroundColor(Color.parseColor("#F04747"))
                                                    btn.isEnabled = false
                                                }
                                            }
                                        } catch (e: Exception) {
                                            // Error aaye toh button hata do taaki app ganda na lage
                                            Utils.mainThread.post { linearLayout.removeView(btn) }
                                        }
                                    }
                                }
                            } catch (e: Throwable) {}
                        }, 200)
                    }
                )
            }
        } catch (e: Throwable) {
            Utils.showToast("Menu Hook Failed: ${e.message}")
        }
    }

    // Ye recursive function ensure karega ki button ekdum sahi layout container me ghuse
    private fun findLinearLayout(view: View): LinearLayout? {
        if (view is LinearLayout) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findLinearLayout(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
        patcher.unpatchAll()
    }
}
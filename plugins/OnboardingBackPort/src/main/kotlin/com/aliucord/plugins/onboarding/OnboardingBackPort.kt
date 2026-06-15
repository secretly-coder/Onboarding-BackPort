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

        // 1. Slash Command (Backup)
        commands.registerCommand(
            "testonboarding",
            "Manually force open Onboarding UI",
            emptyList()
        ) { ctx ->
            val guildId = ctx.channel?.let { it.guildId }
            if (guildId == null || guildId == 0L) {
                return@registerCommand CommandsAPI.CommandResult("Not in a server", null, false)
            }

            Utils.mainThread.postDelayed({
                try {
                    val bottomSheet = OnboardingBottomSheet() 
                    bottomSheet.targetGuildId = guildId.toString() 
                    bottomSheet.show(Utils.appActivity.supportFragmentManager, "OnboardingUI")
                } catch (e: Throwable) {}
            }, 500)

            CommandsAPI.CommandResult("Opening Onboarding...", null, false)
        }

        // 2. SERVER PROFILE BUTTON INJECTION (The Real Deal)
        try {
            val sheetClass = Class.forName("com.discord.widgets.guilds.profile.WidgetGuildProfileSheet")
            
            // Aliucord me configureUI sabse stable target hota hai UI inject karne ke liye
            val configureUiMethod = sheetClass.declaredMethods.find { it.name == "configureUI" }

            if (configureUiMethod != null) {
                patcher.patch(
                    configureUiMethod,
                    Hook { callFrame ->
                        val sheet = callFrame.thisObject
                        
                        // 500ms delay taaki UI fully render ho jaye pehle
                        Utils.mainThread.postDelayed({
                            try {
                                val requireViewMethod = sheet.javaClass.getMethod("requireView")
                                val view = requireViewMethod.invoke(sheet) as? View ?: return@postDelayed
                                
                                val linearLayout = findActionLayout(view)
                                
                                if (linearLayout != null) {
                                    if (linearLayout.findViewWithTag<View>("onboarding_btn") != null) return@postDelayed

                                    val storeStreamClass = Class.forName("com.discord.stores.StoreStream")
                                    val storeGuildSelected = storeStreamClass.getMethod("getGuildSelected").invoke(null)
                                    val guildIdLong = storeGuildSelected.javaClass.getMethod("get").invoke(storeGuildSelected) as Long
                                    val guildId = guildIdLong.toString()

                                    if (guildId == "0" || guildId.isEmpty()) return@postDelayed

                                    // Default Gray checking state
                                    val btn = Button(view.context).apply {
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

                                    linearLayout.addView(btn, 0)

                                    // Background API Check
                                    Utils.threadPool.execute {
                                        try {
                                            val request = Http.Request.newDiscordRequest("/guilds/$guildId/onboarding")
                                            val response = request.execute()

                                            if (response.statusCode == 200) {
                                                val data = response.json(OnboardingResponse::class.java)
                                                
                                                if (data != null && data.enabled == true && !data.prompts.isNullOrEmpty()) {
                                                    Utils.mainThread.post {
                                                        btn.text = "Server Onboarding (Click to Setup)"
                                                        btn.setBackgroundColor(Color.parseColor("#43B581")) // Green
                                                        btn.setOnClickListener {
                                                            val bottomSheet = OnboardingBottomSheet()
                                                            bottomSheet.targetGuildId = guildId
                                                            bottomSheet.show(Utils.appActivity.supportFragmentManager, "OnboardingUI")
                                                        }
                                                    }
                                                } else {
                                                    Utils.mainThread.post {
                                                        btn.text = "Onboarding Disabled"
                                                        btn.setBackgroundColor(Color.parseColor("#F04747")) // Red
                                                        btn.isEnabled = false
                                                    }
                                                }
                                            } else {
                                                Utils.mainThread.post { linearLayout.removeView(btn) }
                                            }
                                        } catch (e: Exception) {
                                            Utils.mainThread.post { linearLayout.removeView(btn) }
                                        }
                                    }
                                }
                            } catch (e: Throwable) {}
                        }, 500)
                    }
                )
            }
        } catch (e: Throwable) {}
    }

    // Helper function to dynamically find the correct UI container
    private fun findActionLayout(view: View): LinearLayout? {
        if (view is LinearLayout && view.orientation == LinearLayout.VERTICAL && view.childCount > 1) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findActionLayout(view.getChildAt(i))
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
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

        // 1. Slash Command (Manual Backup)
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

        // 2. THE UNSTOPPABLE MENU HOOK
        try {
            val sheetClass = Class.forName("com.discord.widgets.guilds.profile.WidgetGuildProfileSheet")
            
            // onViewCreated NEVER fails to trigger in Android
            patcher.patch(
                sheetClass.getDeclaredMethod("onViewCreated", View::class.java, Bundle::class.java),
                Hook { callFrame ->
                    val view = callFrame.args[0] as View
                    val ctx = view.context

                    // 500ms delay to let Discord build its layout first
                    Utils.mainThread.postDelayed({
                        try {
                            val linearLayout = findMainLayout(view)
                            
                            if (linearLayout != null) {
                                // Agar button pehle se hai toh dobara mat lagao
                                if (linearLayout.findViewWithTag<View>("onboarding_btn") != null) return@postDelayed

                                val storeStreamClass = Class.forName("com.discord.stores.StoreStream")
                                val storeGuildSelected = storeStreamClass.getMethod("getGuildSelected").invoke(null)
                                val guildIdLong = storeGuildSelected.javaClass.getMethod("get").invoke(storeGuildSelected) as Long
                                val guildId = guildIdLong.toString()

                                if (guildId == "0" || guildId.isEmpty()) return@postDelayed

                                val btn = Button(ctx).apply {
                                    tag = "onboarding_btn"
                                    text = "Checking Onboarding..."
                                    setTextColor(Color.WHITE)
                                    setBackgroundColor(Color.GRAY)
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT
                                    ).apply { setMargins(40, 0, 40, 20) }
                                    isAllCaps = false
                                }

                                // 0 index matlab sabse upar inject hoga!
                                linearLayout.addView(btn, 0)

                                // Background me status check karo
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
                            } else {
                                // Debugging toast agar layout na mile
                                Utils.showToast("Could not find layout to inject button")
                            }
                        } catch (e: Throwable) {}
                    }, 500)
                }
            )
        } catch (e: Throwable) {}
    }

    // Aggressive layout finder jo ScrollView dhoondh kar uska content nikalega
    private fun findMainLayout(view: View): LinearLayout? {
        // Option 1: ScrollView ke andar ka direct LinearLayout (Sabse reliable)
        if (view.javaClass.name.contains("ScrollView") && view is ViewGroup) {
            val child = view.getChildAt(0)
            if (child is LinearLayout && child.orientation == LinearLayout.VERTICAL) {
                return child
            }
        }
        
        // Option 2: Koi bhi bada vertical layout jisme kam se kam 2 items hon
        if (view is LinearLayout && view.orientation == LinearLayout.VERTICAL && view.childCount >= 2) {
            return view
        }
        
        // Option 3: Recursive deep search
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findMainLayout(view.getChildAt(i))
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
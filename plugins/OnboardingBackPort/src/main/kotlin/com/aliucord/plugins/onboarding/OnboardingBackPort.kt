package com.aliucord.plugins.onboarding

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout
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
        
        // 1. Slash Command 
        commands.registerCommand(
            "onboarding",
            "Open the Server Onboarding UI (if available)",
            emptyList()
        ) { ctx ->
            val guildId = ctx.channel?.let { it.guildId }
            if (guildId == null || guildId == 0L) {
                return@registerCommand CommandsAPI.CommandResult("Not in a server", null, false)
            }

            Utils.mainThread.post {
                try {
                    val bottomSheet = OnboardingBottomSheet()
                    bottomSheet.targetGuildId = guildId.toString()
                    bottomSheet.show(Utils.appActivity.supportFragmentManager, "OnboardingUI")
                } catch (e: Throwable) {}
            }

            CommandsAPI.CommandResult("Opening Onboarding...", null, false)
        }

        // 2. THE AGGRESSIVE ROOT INJECTOR FOR THE BUTTON
        try {
            val sheetClass = Class.forName("com.discord.widgets.guilds.profile.WidgetGuildProfileSheet")
            
            patcher.patch(
                sheetClass.getDeclaredMethod("onViewCreated", View::class.java, Bundle::class.java),
                Hook { callFrame ->
                    val rootFragmentView = callFrame.args[0] as? ViewGroup ?: return@Hook
                    val ctx = rootFragmentView.context

                    Utils.mainThread.postDelayed({
                        try {
                            // If Btn Already then dont make it again
                            if (rootFragmentView.findViewWithTag<View>("onboarding_btn") != null) return@postDelayed

                            val storeStreamClass = Class.forName("com.discord.stores.StoreStream")
                            val storeGuildSelected = storeStreamClass.getMethod("getGuildSelected").invoke(null)
                            val guildIdLong = storeGuildSelected.javaClass.getMethod("get").invoke(storeGuildSelected) as Long
                            val guildId = guildIdLong.toString()

                            if (guildId == "0" || guildId.isEmpty()) return@postDelayed

                            // Button design
                            val btn = Button(ctx).apply {
                                tag = "onboarding_btn"
                                text = "Checking Onboarding..."
                                setTextColor(Color.WHITE)
                                setBackgroundColor(Color.GRAY)
                                isAllCaps = false
                            }

                            // Param So it dont crashes
                            if (rootFragmentView is LinearLayout) {
                                btn.layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { setMargins(40, 20, 40, 20) }
                            } else {
                                btn.layoutParams = FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT
                                ).apply { setMargins(40, 20, 40, 20) }
                            }

                            // Injecting at the absolute top (Index 0) of the whole sheet
                            rootFragmentView.addView(btn, 0)

                            // Background status check
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
                                        Utils.mainThread.post { rootFragmentView.removeView(btn) }
                                    }
                                } catch (e: Exception) {
                                    Utils.mainThread.post { rootFragmentView.removeView(btn) }
                                }
                            }
                        } catch (e: Throwable) {}
                    }, 300)
                }
            )
        } catch (e: Throwable) {}
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
        patcher.unpatchAll()
    }
}
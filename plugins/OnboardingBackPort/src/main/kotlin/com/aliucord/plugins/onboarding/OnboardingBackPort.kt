package com.aliucord.plugins.onboarding

import android.content.Context
import android.os.Bundle
import android.view.View
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
        
        // 1. Manual Command (Fallback ke liye)
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
                } catch (e: Throwable) {
                    com.aliucord.Utils.showToast("Launch Error: ${e.message}")
                }
            }, 1500)

            CommandsAPI.CommandResult("Opening Onboarding...", null, false)
        }

        // 2. SMARTER AUTO-POPUP MAGIC
        try {
            val widgetClass = Class.forName("com.discord.widgets.channels.list.WidgetChannelsList")
            
            // FIX: Dynamically find the right UI method to hook so it never throws NoSuchMethodException
            val targetMethod = widgetClass.declaredMethods.firstOrNull { 
                it.name == "onViewBound" || it.name == "configureUI" || it.name == "onResume" 
            }

            if (targetMethod != null) {
                patcher.patch(
                    targetMethod,
                    Hook { _ ->
                        Utils.threadPool.execute {
                            try {
                                val storeStreamClass = Class.forName("com.discord.stores.StoreStream")
                                val storeGuildSelected = storeStreamClass.getMethod("getGuildSelected").invoke(null)
                                val guildIdLong = storeGuildSelected.javaClass.getMethod("get").invoke(storeGuildSelected) as Long
                                val guildId = guildIdLong.toString()

                                if (guildId != "0" && guildId.isNotEmpty()) {
                                    
                                    val checkedStr = settings.getString("checked_guilds", "")
                                    val checkedGuilds = checkedStr.split(",").filter { it.isNotEmpty() }.toMutableSet()
                                    
                                    if (!checkedGuilds.contains(guildId)) {
                                        checkedGuilds.add(guildId)
                                        settings.setString("checked_guilds", checkedGuilds.joinToString(","))

                                        val request = Http.Request.newDiscordRequest("/guilds/$guildId/onboarding")
                                        val response = request.execute()

                                        if (response.statusCode == 200) {
                                            val data = response.json(OnboardingResponse::class.java)
                                            
                                            // Extra safe check before Auto-popping
                                            if (data != null && data.enabled == true && !data.prompts.isNullOrEmpty()) {
                                                Utils.mainThread.postDelayed({
                                                    try {
                                                        val bottomSheet = OnboardingBottomSheet()
                                                        bottomSheet.targetGuildId = guildId
                                                        bottomSheet.show(Utils.appActivity.supportFragmentManager, "OnboardingUI")
                                                    } catch (e: Throwable) {}
                                                }, 1500)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore background loop errors
                            }
                        }
                    }
                )
            } else {
                Utils.showToast("Could not find suitable UI method to hook for Auto-Popup")
            }
        } catch (e: Throwable) {
            Utils.showToast("Auto-Popup Patch Failed: ${e.message}")
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
        patcher.unpatchAll()
    }
}
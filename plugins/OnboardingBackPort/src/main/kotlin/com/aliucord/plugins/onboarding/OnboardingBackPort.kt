package com.aliucord.plugins.onboarding

import android.content.Context
import android.os.Bundle
import android.view.View
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.PinePatchFn
import com.aliucord.plugins.onboarding.models.OnboardingResponse
import com.aliucord.plugins.onboarding.ui.OnboardingBottomSheet

@AliucordPlugin
class OnboardingBackPort : Plugin() {

    override fun start(context: Context) {
        // 1. Manual Command (Testing ke liye rakha hai, just in case)
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

        // 2. AUTO-POPUP MAGIC (The Real Deal)
        try {
            val widgetClass = Class.forName("com.discord.widgets.channels.list.WidgetChannelsList")
            
            patcher.patch(
                widgetClass.getDeclaredMethod("onViewCreated", View::class.java, Bundle::class.java),
                PinePatchFn {
                    Utils.threadPool.execute {
                        try {
                            // Current Server ID nikalna
                            val storeStreamClass = Class.forName("com.discord.stores.StoreStream")
                            val storeGuildSelected = storeStreamClass.getMethod("getGuildSelected").invoke(null)
                            val guildIdLong = storeGuildSelected.javaClass.getMethod("get").invoke(storeGuildSelected) as Long
                            val guildId = guildIdLong.toString()

                            if (guildId != "0" && guildId.isNotEmpty()) {
                                
                                // Hum check karte hain ki kya ye server hum pehle dekh chuke hain
                                val checkedStr = settings.getString("checked_guilds", "")
                                val checkedGuilds = checkedStr.split(",").filter { it.isNotEmpty() }.toMutableSet()
                                
                                if (!checkedGuilds.contains(guildId)) {
                                    // Memory me save kar lo taaki loop na bane
                                    checkedGuilds.add(guildId)
                                    settings.setString("checked_guilds", checkedGuilds.joinToString(","))

                                    // Chup-chaap Background API check (No loading screens)
                                    val request = Http.Request.newDiscordRequest("/guilds/$guildId/onboarding")
                                    val response = request.execute()

                                    if (response.statusCode == 200) {
                                        val data = response.json(OnboardingResponse::class.java)
                                        
                                        // Agar onboarding ON hai, toh BHOOM! Popup karo!
                                        if (data != null && data.enabled == true) {
                                            Utils.mainThread.postDelayed({
                                                try {
                                                    val bottomSheet = OnboardingBottomSheet()
                                                    bottomSheet.targetGuildId = guildId
                                                    bottomSheet.show(Utils.appActivity.supportFragmentManager, "OnboardingUI")
                                                } catch (e: Throwable) {}
                                            }, 1500) // Delay taaki chat aaram se load ho jaye
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // Silent reflection errors
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            Utils.showToast("Auto-Popup Patch Failed: ${e.message}")
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
        patcher.unpatchAll()
    }
}
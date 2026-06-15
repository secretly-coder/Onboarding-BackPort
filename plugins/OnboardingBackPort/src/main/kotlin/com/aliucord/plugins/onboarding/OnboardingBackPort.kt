package com.aliucord.plugins.onboarding

import android.content.Context
import com.aliucord.Http
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.plugins.onboarding.models.OnboardingResponse
import com.aliucord.plugins.onboarding.ui.OnboardingBottomSheet

@AliucordPlugin
class OnboardingBackPort : Plugin() {

    override fun start(context: Context) {
        
        // Slash Cmd
        commands.registerCommand(
            "onboarding",
            "Open the Server Onboarding UI (if available)",
            emptyList()
        ) { ctx ->
            val guildId = ctx.channel?.let { it.guildId }
            if (guildId == null || guildId == 0L) {
                return@registerCommand CommandsAPI.CommandResult("You must use this inside a server.", null, false)
            }

            // Background API Check
            Utils.threadPool.execute {
                try {
                    val request = Http.Request.newDiscordRequest("/guilds/$guildId/onboarding")
                    val response = request.execute()

                    if (response.statusCode == 200) {
                        val data = response.json(OnboardingResponse::class.java)
                        
                        if (data != null && data.enabled == true && !data.prompts.isNullOrEmpty()) {
                            // Onboarding is valid, show the UI
                            Utils.mainThread.post {
                                try {
                                    val bottomSheet = OnboardingBottomSheet()
                                    bottomSheet.targetGuildId = guildId.toString()
                                    bottomSheet.show(Utils.appActivity.supportFragmentManager, "OnboardingUI")
                                } catch (e: Throwable) {}
                            }
                        } else {
                            // Onboarding is disabled or empty
                            Utils.mainThread.post {
                                Utils.showToast("Onboarding is disabled or not fully setup for this server.")
                            }
                        }
                    } else {
                        Utils.mainThread.post {
                            Utils.showToast("Failed to check Onboarding (Code: ${response.statusCode})")
                        }
                    }
                } catch (e: Exception) {
                    Utils.mainThread.post {
                        Utils.showToast("API Error: ${e.message}")
                    }
                }
            }

            // Silent response in chat while checking
            CommandsAPI.CommandResult("Checking Onboarding Status...", null, false)
        }
    }

    override fun stop(context: Context) {
        // Cleaned up unpatchAll() since we aren't hooking anything anymore
        commands.unregisterAll()
    }
}
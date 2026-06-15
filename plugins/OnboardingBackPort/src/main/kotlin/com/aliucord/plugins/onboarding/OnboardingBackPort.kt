package com.aliucord.plugins.onboarding

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.plugins.onboarding.ui.OnboardingBottomSheet

@AliucordPlugin
class OnboardingBackPort : Plugin() {

    override fun start(context: Context) {
        commands.registerCommand(
            "testonboarding",
            "Test the Custom Onboarding UI for the current server",
            emptyList()
        ) { ctx ->
            val guildId = ctx.channel?.let { it.guildId }
            
            if (guildId == null || guildId == 0L) {
                return@registerCommand CommandsAPI.CommandResult("Not in a server", null, false)
            }

            // 1.5s delay ensures keyboard closing animation doesn't kill our UI
            com.aliucord.Utils.mainThread.postDelayed({
                try {
                    // ZERO parameter constructor. 100% Android compliant.
                    val bottomSheet = OnboardingBottomSheet() 
                    
                    // Passing data directly to the property
                    bottomSheet.targetGuildId = guildId.toString() 
                    
                    bottomSheet.show(com.aliucord.Utils.appActivity.supportFragmentManager, "OnboardingUI")
                } catch (e: Throwable) {
                    com.aliucord.Utils.showToast("Launch Error: ${e.message}")
                }
            }, 1500)

            CommandsAPI.CommandResult("Opening Onboarding in 1.5s...", null, false)
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }
}
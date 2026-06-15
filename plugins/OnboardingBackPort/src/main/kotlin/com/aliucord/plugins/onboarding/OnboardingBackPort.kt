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
                return@registerCommand CommandsAPI.CommandResult(
                    "You must use this command inside a server.",
                    null,
                    false
                )
            }

            com.aliucord.Utils.mainThread.postDelayed({
                try {
                    com.aliucord.Utils.showToast("1. Triggering UI...")
                    
                    val bottomSheet = OnboardingBottomSheet(guildId.toString())
                    bottomSheet.show(com.aliucord.Utils.appActivity.supportFragmentManager, "OnboardingUI")
                    
                    com.aliucord.Utils.showToast("2. UI Show Commanded!")
                } catch (e: Throwable) {
                    com.aliucord.Utils.showToast("Crash: ${e.message}")
                }
            }, 300)

            CommandsAPI.CommandResult("Opening Onboarding UI...", null, false)
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }
}
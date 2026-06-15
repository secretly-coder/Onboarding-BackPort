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

            // FIX: Added a 500ms delay. This prevents Discord's chat refresh 
            // from instantly dismissing the BottomSheet.
            com.aliucord.Utils.mainThread.postDelayed({
                try {
                    val bottomSheet = OnboardingBottomSheet(guildId.toString())
                    val fragmentManager = com.aliucord.Utils.appActivity.supportFragmentManager
                    bottomSheet.show(fragmentManager, "OnboardingUI")
                } catch (e: Throwable) {
                    com.aliucord.Utils.showToast("UI Error: ${e.message}")
                }
            }, 500)

            CommandsAPI.CommandResult("Opening Onboarding UI...", null, false)
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }
}
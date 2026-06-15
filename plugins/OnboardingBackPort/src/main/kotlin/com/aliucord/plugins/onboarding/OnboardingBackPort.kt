package com.aliucord.plugins.onboarding

import android.content.Context
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin
import com.aliucord.plugins.onboarding.ui.OnboardingBottomSheet

@AliucordPlugin
class OnboardingBackPort : Plugin() {

    override fun start(context: Context) {
        // Registering a chat command to trigger the onboarding UI manually for testing
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

            // Open the BottomSheet on the main thread
            com.aliucord.Utils.threadPool.execute {
                val bottomSheet = OnboardingBottomSheet(guildId.toString())
                bottomSheet.show(ctx.parent.parentFragmentManager, "OnboardingUI")
            }

            CommandsAPI.CommandResult("Opening Onboarding UI...", null, false)
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }
}
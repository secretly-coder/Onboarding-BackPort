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
        ) { 
            // 1 Pura second delay
            com.aliucord.Utils.mainThread.postDelayed({
                try {
                    val bottomSheet = OnboardingBottomSheet()
                    // Normal show() use kar rahe hain ab
                    bottomSheet.show(com.aliucord.Utils.appActivity.supportFragmentManager, "OnboardingUI")
                } catch (e: Throwable) {
                    com.aliucord.Utils.showToast("Crash: ${e.message}")
                }
            }, 1000)

            CommandsAPI.CommandResult("Testing Basic UI...", null, false)
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }
}
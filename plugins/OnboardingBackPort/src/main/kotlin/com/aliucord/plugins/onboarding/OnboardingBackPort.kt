package com.aliucord.plugins.onboarding

import android.content.Context
import com.aliucord.Http
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.CommandsAPI
import com.aliucord.entities.Plugin

@AliucordPlugin
class OnboardingBackPort : Plugin() {

    override fun start(context: Context) {
        commands.registerCommand(
            "testonboarding",
            "Force test the Discord API directly",
            emptyList()
        ) { ctx ->
            val guildId = ctx.channel?.let { it.guildId } 
            
            if (guildId == null || guildId == 0L) {
                return@registerCommand CommandsAPI.CommandResult("Not in a server.", null, false)
            }

            try {
                // Testing if the API is actually returning data or throwing an unhandled exception
                val request = Http.Request.newDiscordRequest("/guilds/$guildId/onboarding")
                val response = request.execute()
                
                // If it succeeds, it will print the exact JSON data in chat
                val resultText = "Status: ${response.statusCode}\nData: ${response.text().take(500)}..."
                return@registerCommand CommandsAPI.CommandResult(resultText, null, false)
                
            } catch (e: Exception) {
                // If it fails, we will finally see the EXACT error
                return@registerCommand CommandsAPI.CommandResult("API CRASHED: ${e.message}", null, false)
            }
        }
    }

    override fun stop(context: Context) {
        commands.unregisterAll()
    }
}
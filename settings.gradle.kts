plugins {
    id("com.aliucord.gradle.plugin") version "0.2.1"
}

aliucord {
    pluginName.set("OnboardingBackPort")
    description.set("Backports Discord's server onboarding feature to Aliucord.")
    authors.add("Ribro")
}

dependencies {
    // Nope
}
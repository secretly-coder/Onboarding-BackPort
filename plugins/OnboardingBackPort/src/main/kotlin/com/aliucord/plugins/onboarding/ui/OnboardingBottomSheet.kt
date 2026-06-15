package com.aliucord.plugins.onboarding.ui

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.aliucord.widgets.BottomSheet

class OnboardingBottomSheet : BottomSheet() {

    override fun onViewCreated(view: View, bundle: Bundle?) {
        super.onViewCreated(view, bundle)

        // Ekdum simple test text
        val testText = TextView(view.context).apply {
            text = "IT WORKS BRO!"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(100, 100, 100, 100)
        }

        // Direct addView (Aliucord khud isko handle kar lega)
        addView(testText)
    }
}
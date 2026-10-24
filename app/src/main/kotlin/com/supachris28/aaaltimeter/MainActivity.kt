package com.supachris28.aaaltimeter

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/** Minimal phone-side screen. The real UI lives on the car screen. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()
        setContentView(
            TextView(this).apply {
                setText(R.string.phone_message)
                textSize = 18f
                setPadding(padding, padding, padding, padding)
            }
        )
    }
}

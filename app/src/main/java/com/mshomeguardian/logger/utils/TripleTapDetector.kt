package com.mshomeguardian.logger.utils

import android.view.View
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat

/**
 * Detects triple-tap gestures on a View
 */
class TripleTapDetector(
    private val view: View,
    private val onTripleTap: () -> Unit
) : View.OnTouchListener {
    
    private var tapCount = 0
    private var lastTapTime = 0L
    private val tapThreshold = 300L // ms between taps
    
    init {
        view.setOnTouchListener(this)
    }
    
    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event == null) return false
        
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - lastTapTime < tapThreshold) {
                    tapCount++
                } else {
                    tapCount = 1
                }
                
                lastTapTime = currentTime
                
                if (tapCount == 3) {
                    tapCount = 0
                    onTripleTap()
                }
                return true
            }
        }
        return false
    }
}

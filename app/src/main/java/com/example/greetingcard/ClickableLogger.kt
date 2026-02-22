package com.example.greetingcard

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

object ClickableLogger {

    fun logAllClickables(rootNode: AccessibilityNodeInfo?, pkg: String) {
        if (rootNode == null) {
            Log.w("ClickableLogger", "⚠️ No active window node found for $pkg")
            return
        }

        Log.d("ClickableLogger", "🔍 Logging clickable nodes for package: $pkg")

        fun traverse(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null) return

            if (node.isClickable) {
                val info = buildString {
                    append("• ")

                    // Add visible text or description
                    val text = node.text?.toString()?.trim()
                    val desc = node.contentDescription?.toString()?.trim()
                    if (!text.isNullOrEmpty()) append("text='$text' ")
                    if (!desc.isNullOrEmpty()) append("desc='$desc' ")

                    // Add class
                    append("class=${node.className} ")

                    // Add viewId if available (helps a lot!)
                    node.viewIdResourceName?.let { append("id=$it ") }

                    // Add checkable/checked states
                    if (node.isCheckable) append("checkable=${node.isChecked} ")

                    // Add bounds (where it is on screen)
                    val rect = android.graphics.Rect()
                    node.getBoundsInScreen(rect)
                    append("bounds=$rect")
                }

                Log.d("ClickableLogger", "${" ".repeat(depth * 2)}$info")
            }

            // Recurse children
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i), depth + 1)
            }
        }

        traverse(rootNode, 0)
    }
}

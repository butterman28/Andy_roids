package com.example.greetingcard

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class YouTubeAppHandler(private val service: YoutubeAccessibilityService) {

    companion object {
        private const val TAG = "YouTubeAppHandler"
        private const val SHARE_BUTTON_DESC = "Share"
        private const val MORE_BUTTON_TEXT = "More"
        private const val GREETING_CARD_APP = "Greeting Card"

        // Alternative texts for More button
        private val MORE_BUTTON_ALTERNATIVES = listOf(
            "More", "More options", "See more", "View more", "Show more apps",
            "More apps", "Other apps", "Additional apps", "See all"
        )

        // Swipes - adjusted timing and attempts
        private const val SWIPE_DURATION_MS = 300L
        private const val LEFT_SWIPE_ATTEMPTS = 8
        private const val RIGHT_SWIPE_ATTEMPTS = 4
        private const val AFTER_SWIPE_CHECK_DELAY_MS = 800L
        private const val INITIAL_DIALOG_DELAY_MS = 2000L
    }

    fun handleShareFlow() {
        try {
            val rootNode = service.rootInActiveWindow ?: return
            Log.d(TAG, "Starting YouTube app share flow")

            val shareButton = findNodeByDescription(rootNode, SHARE_BUTTON_DESC)
            if (shareButton != null) {
                Log.d(TAG, "Found Share button, clicking it")
                if (tryClickNodeAndParents(shareButton, "Share")) {
                    service.showToast("Share button clicked")
                    Handler(Looper.getMainLooper()).postDelayed({
                        handleShareDialog()
                    }, INITIAL_DIALOG_DELAY_MS)
                } else {
                    service.showToast("Failed to click Share button")
                }
            } else {
                service.showToast("Share button not found")
                debugClickableNodes(rootNode)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleShareFlow", e)
            service.showToast("Error accessing share button")
        }
    }

    private fun handleShareDialog() {
        try {
            val rootNode = service.rootInActiveWindow ?: return
            Log.d(TAG, "Looking for More button in share dialog")

            // Check if Greeting Card is already visible first
            val greetingCardNode = findNodeWithText(rootNode, GREETING_CARD_APP)
                ?: findNodeByDescription(rootNode, GREETING_CARD_APP)

            if (greetingCardNode != null) {
                Log.d(TAG, "Greeting Card app already visible, clicking directly")
                if (tryClickNodeAndParents(greetingCardNode, "Greeting Card")) {
                    service.showToast("Greeting Card app selected!")
                    return
                }
            }

            // Look for More button with alternative texts
            var moreButton: AccessibilityNodeInfo? = null
            for (moreText in MORE_BUTTON_ALTERNATIVES) {
                moreButton = findNodeWithText(rootNode, moreText) ?: findNodeByDescription(rootNode, moreText)
                if (moreButton != null) {
                    Log.d(TAG, "Found More button with text: '$moreText'")
                    break
                }
            }

            if (moreButton != null) {
                Log.d(TAG, "More button visible immediately, clicking")
                if (tryClickNodeAndParents(moreButton, "More")) {
                    service.showToast("More button clicked")
                    Handler(Looper.getMainLooper()).postDelayed({
                        findAndClickGreetingCardApp()
                    }, 2000)
                    return
                }
            }

            // Try to find scrollable container first
            val shareContainer = findScrollableShareContainer(rootNode)
            if (shareContainer != null) {
                Log.d(TAG, "Found scrollable share container, performing targeted swipes")
                logContainerInfo(shareContainer)
                swipeContainerUntilMoreFound(shareContainer)
            } else {
                Log.d(TAG, "No scrollable container found, trying full-screen swipes")
                performFullScreenSwipeAttempts()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleShareDialog", e)
            service.showToast("Error in share dialog")
        }
    }

    private fun findAndClickGreetingCardApp() {
        try {
            val rootNode = service.rootInActiveWindow ?: return
            Log.d(TAG, "=== Looking for Greeting Card app in expanded dialog ===")

            // Use the same approach as lookForGreetingCardApp()
            val greetingCardNodes = rootNode.findAccessibilityNodeInfosByText(GREETING_CARD_APP)

            if (greetingCardNodes.isNotEmpty()) {
                val appNode = greetingCardNodes[0]
                if (tryClickNodeAndParents(appNode, GREETING_CARD_APP)) {
                    service.showToast("Greeting Card app selected!")
                    return
                }
            }

            // fallback: check description in case text doesn’t match
            val descNode = findNodeByDescription(rootNode, GREETING_CARD_APP)
            if (descNode != null && tryClickNodeAndParents(descNode, GREETING_CARD_APP)) {
                service.showToast("Greeting Card app selected by description!")
                return
            }

            service.showToast("Greeting Card app not found in share dialog")
            debugClickableNodes(rootNode)  // dump full dialog tree for debugging

        } catch (e: Exception) {
            Log.e(TAG, "Error finding Greeting Card app", e)
            service.showToast("Error finding Greeting Card app")
        }
    }



    // ---------------------
    // Helper search functions
    // ---------------------

    private fun findNodeWithText(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val text = node.text?.toString() ?: ""
        if (text.contains(target, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeWithText(child, target)
            if (result != null) return result
        }
        return null
    }

    private fun findNodeByDescription(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val desc = node.contentDescription?.toString() ?: ""
        if (desc.contains(target, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByDescription(child, target)
            if (result != null) return result
        }
        return null
    }

    private fun tryClickNodeAndParents(node: AccessibilityNodeInfo, label: String): Boolean {
        var current: AccessibilityNodeInfo? = node
        var level = 0
        while (current != null && level < 6) {
            if (current.isClickable && current.isEnabled) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "$label click at level $level: $clicked (class=${current.className})")
                if (clicked) return true
            }
            current = current.parent
            level++
        }
        return false
    }

    private fun findAllClickableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isClickable && node.isEnabled) list.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllClickableNodes(child, list)
        }
    }

    private fun debugClickableNodes(root: AccessibilityNodeInfo) {
        Log.d(TAG, "=== Clickable nodes ===")
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        findAllClickableNodes(root, nodes)
        for ((i, node) in nodes.withIndex()) {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val id = node.viewIdResourceName ?: ""
            val cls = node.className?.toString() ?: ""
            Log.d(TAG, "$i: text='$text', desc='$desc', id='$id', class='$cls'")
        }
    }

    // ---------------------
    // Container detection and swiping
    // ---------------------

    private fun logContainerInfo(container: AccessibilityNodeInfo) {
        val bounds = Rect()
        container.getBoundsInScreen(bounds)
        Log.d(TAG, "Container details:")
        Log.d(TAG, "  Class: ${container.className}")
        Log.d(TAG, "  ID: ${container.viewIdResourceName}")
        Log.d(TAG, "  Bounds: $bounds")
        Log.d(TAG, "  Width: ${bounds.width()}, Height: ${bounds.height()}")
        Log.d(TAG, "  Children: ${container.childCount}")
        Log.d(TAG, "  Scrollable: ${container.isScrollable}")
    }

    private fun findScrollableShareContainer(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findScrollableContainer(node, 0)
    }

    private fun findScrollableContainer(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 10) return null

        val cls = node.className?.toString() ?: ""
        val id = node.viewIdResourceName ?: ""

        val isScrollable = node.isScrollable ||
                cls.contains("RecyclerView", ignoreCase = true) ||
                cls.contains("HorizontalScrollView", ignoreCase = true) ||
                cls.contains("ViewPager", ignoreCase = true) ||
                cls.contains("ScrollView", ignoreCase = true)

        val isShareRelated = id.contains("share", ignoreCase = true) ||
                id.contains("resolver", ignoreCase = true) ||
                id.contains("app", ignoreCase = true) ||
                id.contains("chooser", ignoreCase = true)

        if (isScrollable && (isShareRelated || node.childCount > 3)) {
            Log.d(TAG, "Found scrollable container: class=$cls, id=$id, children=${node.childCount}")
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableContainer(child, depth + 1)
            if (found != null) return found
        }

        return null
    }

    private fun swipeContainerUntilMoreFound(container: AccessibilityNodeInfo) {
        val bounds = Rect()
        container.getBoundsInScreen(bounds)

        if (bounds.isEmpty) {
            Log.d(TAG, "Container bounds empty, falling back to full-screen swipe")
            performFullScreenSwipeAttempts()
            return
        }

        Log.d(TAG, "Container dimensions: ${bounds.width()}x${bounds.height()}")
        Log.d(TAG, "Container bounds: left=${bounds.left}, right=${bounds.right}")

        // Try accessibility scroll actions first since they work better
        Log.d(TAG, "Trying accessibility scroll actions first (they're more reliable)")
        tryScrollActions(container)
    }

    private fun performAggressiveSwipeSequence(
        startX: Float, startY: Float, endX: Float, endY: Float,
        attempts: Int, direction: String,
        onComplete: (Boolean) -> Unit
    ) {
        var remainingAttempts = attempts
        var consecutiveFailures = 0

        fun performNextSwipe() {
            if (remainingAttempts <= 0) {
                onComplete(false)
                return
            }

            remainingAttempts--
            Log.d(TAG, "Performing $direction swipe, attempts remaining: $remainingAttempts")

            // Vary the swipe slightly each time to avoid getting stuck
            val variation = (consecutiveFailures * 10f).coerceAtMost(30f)
            val adjustedStartX = startX - variation
            val adjustedEndX = endX + variation

            Log.d(TAG, "Swipe with variation: ($adjustedStartX,$startY) to ($adjustedEndX,$endY)")

            val swipeSuccessful = dispatchSwipe(adjustedStartX, startY, adjustedEndX, endY, SWIPE_DURATION_MS)
            Log.d(TAG, "$direction swipe dispatched: $swipeSuccessful")

            if (!swipeSuccessful) {
                consecutiveFailures++
                Log.w(TAG, "Swipe dispatch failed ($consecutiveFailures consecutive failures)")
                if (remainingAttempts > 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        performNextSwipe()
                    }, 300)
                } else {
                    onComplete(false)
                }
                return
            }

            consecutiveFailures = 0

            // Wait for UI to settle and check for target
            Handler(Looper.getMainLooper()).postDelayed({
                val rootNode = service.rootInActiveWindow
                if (rootNode != null) {
                    // Check for More button with all alternatives
                    var moreButton: AccessibilityNodeInfo? = null
                    for (moreText in MORE_BUTTON_ALTERNATIVES) {
                        moreButton = findNodeWithText(rootNode, moreText) ?: findNodeByDescription(rootNode, moreText)
                        if (moreButton != null) {
                            Log.d(TAG, "Found More button with text: '$moreText' after $direction swipe")
                            break
                        }
                    }

                    if (moreButton != null && tryClickNodeAndParents(moreButton, "More")) {
                        Log.d(TAG, "Successfully clicked More button after $direction swipe")
                        onComplete(true)
                        return@postDelayed
                    }

                    // Also check if Greeting Card became visible directly
                    val greetingCardNode = findNodeWithText(rootNode, GREETING_CARD_APP)
                        ?: findNodeByDescription(rootNode, GREETING_CARD_APP)
                    if (greetingCardNode != null) {
                        Log.d(TAG, "Greeting Card became visible after $direction swipe")
                        if (tryClickNodeAndParents(greetingCardNode, "Greeting Card")) {
                            service.showToast("Greeting Card found after $direction swipe!")
                            onComplete(true)
                            return@postDelayed
                        }
                    }

                    // Log what's currently visible for debugging
                    Log.d(TAG, "After swipe - current visible apps:")
                    debugCurrentShareApps(rootNode)
                }

                Log.d(TAG, "Target not found after $direction swipe, remaining attempts: $remainingAttempts")
                if (remainingAttempts > 0) {
                    performNextSwipe()
                } else {
                    onComplete(false)
                }
            }, AFTER_SWIPE_CHECK_DELAY_MS + 200)
        }

        performNextSwipe()
    }

    private fun debugCurrentShareApps(rootNode: AccessibilityNodeInfo) {
        val apps = mutableListOf<String>()
        findShareAppNodes(rootNode, apps)
        Log.d(TAG, "Currently visible share apps: ${apps.joinToString(", ")}")
    }

    private fun findShareAppNodes(node: AccessibilityNodeInfo, apps: MutableList<String>) {
        if (node.className?.toString()?.contains("Button") == true) {
            val desc = node.contentDescription?.toString()
            val text = node.text?.toString()
            if (!desc.isNullOrEmpty() && desc.trim().isNotEmpty()) {
                apps.add(desc)
            } else if (!text.isNullOrEmpty() && text.trim().isNotEmpty()) {
                apps.add(text)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findShareAppNodes(child, apps)
        }
    }

    private fun tryScrollActions(container: AccessibilityNodeInfo) {
        Log.d(TAG, "Trying horizontal scroll actions on container")

        // Try horizontal scrolling only (share dialogs are horizontal)
        val scrolledRight = container.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)

        if (scrolledRight) {
            Log.d(TAG, "Scroll forward (right) action successful")
            Handler(Looper.getMainLooper()).postDelayed({
                checkForMoreButtonAfterScroll("scroll-forward")
            }, AFTER_SWIPE_CHECK_DELAY_MS)
            return
        }

        Log.d(TAG, "No horizontal scroll actions worked, trying full-screen swipes")
        performFullScreenSwipeAttempts()
    }

    private fun checkForMoreButtonAfterScroll(scrollType: String) {
        val rootNode = service.rootInActiveWindow
        if (rootNode != null) {
            // Check for More button with all alternatives
            for (moreText in MORE_BUTTON_ALTERNATIVES) {
                val moreButton = findNodeWithText(rootNode, moreText) ?: findNodeByDescription(rootNode, moreText)
                if (moreButton != null && tryClickNodeAndParents(moreButton, "More")) {
                    Log.d(TAG, "Found and clicked More button after $scrollType")
                    service.showToast("More clicked after $scrollType")
                    Handler(Looper.getMainLooper()).postDelayed({
                        findAndClickGreetingCardApp()
                    }, 4000)
                    return
                }
            }

            // Check if Greeting Card is now visible
            val greetingCardNode = findNodeWithText(rootNode, GREETING_CARD_APP)
                ?: findNodeByDescription(rootNode, GREETING_CARD_APP)
            if (greetingCardNode != null && tryClickNodeAndParents(greetingCardNode, "Greeting Card")) {
                Log.d(TAG, "Found Greeting Card directly after $scrollType")
                service.showToast("Greeting Card found after $scrollType")
                return
            }
        }

        Log.d(TAG, "More button not found after $scrollType")
        performFullScreenSwipeAttempts()
    }

    private fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean {
        return try {
            // Create a more detailed path with intermediate points
            val path = Path().apply {
                moveTo(startX, startY)

                // Add intermediate points for smoother swipe
                val steps = 5
                for (i in 1..steps) {
                    val progress = i.toFloat() / steps
                    val intermediateX = startX + (endX - startX) * progress
                    val intermediateY = startY + (endY - startY) * progress
                    lineTo(intermediateX, intermediateY)
                }
            }

            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            val callback = object : android.accessibilityservice.AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Swipe gesture completed successfully")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Swipe gesture was cancelled")
                }
            }

            val dispatched = service.dispatchGesture(gesture, callback, null)
            Log.d(TAG, "Swipe dispatched: $dispatched from ($startX,$startY) to ($endX,$endY) duration=${durationMs}ms")
            Log.d(TAG, "Gesture details - Distance: ${Math.abs(endX - startX)}px, Direction: ${if (endX > startX) "right" else "left"}")

            dispatched
        } catch (ex: Exception) {
            Log.e(TAG, "Error dispatching swipe", ex)
            false
        }
    }

    // ---------------------
    // Fallback: full-screen swipes
    // ---------------------

    private fun performFullScreenSwipeAttempts() {
        val displayMetrics = service.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        // Swipe in the middle-lower area where share dialog typically appears
        val swipeY = screenHeight * 0.75f
        val margin = screenWidth * 0.1f

        // Left swipe coordinates
        val leftStart = screenWidth - margin
        val leftEnd = margin

        // Right swipe coordinates
        val rightStart = margin
        val rightEnd = screenWidth - margin

        Log.d(TAG, "Full-screen swipe at Y=$swipeY, screen size: ${screenWidth}x$screenHeight")

        performBasicSwipeSequence(leftStart, swipeY, leftEnd, swipeY, LEFT_SWIPE_ATTEMPTS, "full-screen left") { foundLeft ->
            if (foundLeft) {
                service.showToast("Found More after full-screen left swipe")
                Handler(Looper.getMainLooper()).postDelayed({
                    findAndClickGreetingCardApp()
                }, 2000)
            } else {
                performBasicSwipeSequence(rightStart, swipeY, rightEnd, swipeY, RIGHT_SWIPE_ATTEMPTS, "full-screen right") { foundRight ->
                    if (foundRight) {
                        service.showToast("Found More after full-screen right swipe")
                        Handler(Looper.getMainLooper()).postDelayed({
                            findAndClickGreetingCardApp()
                        }, 2000)
                    } else {
                        service.showToast("Could not find More button after all swipe attempts")
                        service.rootInActiveWindow?.let { debugClickableNodes(it) }
                    }
                }
            }
        }
    }

    private fun performBasicSwipeSequence(
        startX: Float, startY: Float, endX: Float, endY: Float,
        attempts: Int, direction: String,
        onComplete: (Boolean) -> Unit
    ) {
        var remainingAttempts = attempts

        fun performNextSwipe() {
            if (remainingAttempts <= 0) {
                onComplete(false)
                return
            }

            remainingAttempts--
            Log.d(TAG, "Performing $direction swipe, attempts remaining: $remainingAttempts")

            val swipeSuccessful = dispatchSwipe(startX, startY, endX, endY, SWIPE_DURATION_MS)
            Log.d(TAG, "$direction swipe dispatched: $swipeSuccessful")

            if (!swipeSuccessful) {
                if (remainingAttempts > 0) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        performNextSwipe()
                    }, 500)
                } else {
                    onComplete(false)
                }
                return
            }

            // Wait for UI to settle, then check for More button or Greeting Card
            Handler(Looper.getMainLooper()).postDelayed({
                val rootNode = service.rootInActiveWindow
                if (rootNode != null) {
                    // Check for More button with all alternatives
                    var moreButton: AccessibilityNodeInfo? = null
                    for (moreText in MORE_BUTTON_ALTERNATIVES) {
                        moreButton = findNodeWithText(rootNode, moreText) ?: findNodeByDescription(rootNode, moreText)
                        if (moreButton != null) break
                    }

                    if (moreButton != null && tryClickNodeAndParents(moreButton, "More")) {
                        Log.d(TAG, "Successfully clicked More button after $direction swipe")
                        onComplete(true)
                        return@postDelayed
                    }

                    // Also check if Greeting Card became visible directly
                    val greetingCardNode = findNodeWithText(rootNode, GREETING_CARD_APP)
                        ?: findNodeByDescription(rootNode, GREETING_CARD_APP)
                    if (greetingCardNode != null) {
                        Log.d(TAG, "Greeting Card became visible after $direction swipe")
                        if (tryClickNodeAndParents(greetingCardNode, "Greeting Card")) {
                            service.showToast("Greeting Card found after $direction swipe!")
                            onComplete(true)
                            return@postDelayed
                        }
                    }
                }

                Log.d(TAG, "Target not found after $direction swipe, remaining attempts: $remainingAttempts")
                if (remainingAttempts > 0) {
                    performNextSwipe()
                } else {
                    onComplete(false)
                }
            }, AFTER_SWIPE_CHECK_DELAY_MS)
        }

        performNextSwipe()
    }
}
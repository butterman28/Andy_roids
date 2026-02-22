package com.example.greetingcard

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class YoutubeAccessibilityService : AccessibilityService() {

    companion object {
        var currentUrl: String? = null
        var instance: YoutubeAccessibilityService? = null
        private var lastPackage: String? = null

        fun getCurrentPackage(): String? {
            return instance?.getCurrentForegroundPackage()
        }

        fun fetchBrowserUrl() {
            instance?.let { service ->
                val root = service.rootInActiveWindow ?: return
                val pkg = getCurrentPackage() ?: return
                service.parseBrowser(root, pkg, manual = true)
            }
        }

        fun triggerShareFlow() {
            instance?.clickShareAndSelectApp()
        }
    }

    private lateinit var youtubeAppHandler: YouTubeAppHandler

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        youtubeAppHandler = YouTubeAppHandler(this)
        setupClipboardListener()
    }

    fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            lastPackage = it.packageName?.toString()
        }
    }

    private fun getCurrentForegroundPackage(): String? {
        try {
            val root = rootInActiveWindow
            root?.packageName?.toString()?.let { return it }

            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val tasks = activityManager.getRunningTasks(1)
            if (tasks.isNotEmpty()) {
                return tasks[0].topActivity?.packageName
            }
        } catch (e: Exception) {
            Log.e("YTService", "Error getting current package", e)
        }
        return lastPackage
    }

    private fun setupClipboardListener() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener {
            val clip: ClipData? = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (text != null && text.contains("youtube.com/")) {
                    currentUrl = text
                    Toast.makeText(applicationContext, "YouTube link copied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseBrowser(rootNode: AccessibilityNodeInfo, packageName: String, manual: Boolean = false) {
        try {
            val urlBarId = when (packageName) {
                "com.android.chrome" -> "com.android.chrome:id/url_bar"
                "org.mozilla.firefox" -> "org.mozilla.firefox:id/url_bar_title"
                else -> {
                    if (manual) Toast.makeText(applicationContext, "Unsupported browser", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            val urlNodes = rootNode.findAccessibilityNodeInfosByViewId(urlBarId)
            if (urlNodes.isNotEmpty()) {
                val url = urlNodes[0].text?.toString()
                if (url != null && url.contains("youtube.com/watch")) {
                    currentUrl = url
                    currentUrl?.let { url ->
                        DownloadManager.downloadAudio(applicationContext, url)
                    }

                    if (manual) Toast.makeText(applicationContext, "URL fetched", Toast.LENGTH_SHORT).show()
                } else if (manual) {
                    Toast.makeText(applicationContext, "Not a YouTube watch URL", Toast.LENGTH_SHORT).show()
                }
            } else if (manual) {
                Toast.makeText(applicationContext, "URL bar not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("YTService", "Error parsing browser UI", e)
            if (manual) Toast.makeText(applicationContext, "Error parsing browser", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clickShareAndSelectApp() {
        val rootNode = rootInActiveWindow ?: return
        val currentPackage = getCurrentForegroundPackage()
        Log.d("YTService", "Current package: $currentPackage")

        when {
            // Handle YouTube Music
            currentPackage?.contains("music") == true && currentPackage.contains("youtube") -> {
                handleYouTubeMusicShareFlow()
            }
            // Handle regular YouTube app
            currentPackage?.contains("youtube") == true -> {
                youtubeAppHandler.handleShareFlow()
            }
            else -> {
                Toast.makeText(applicationContext, "Unsupported app: $currentPackage", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleYouTubeMusicShareFlow() {
        val rootNode = rootInActiveWindow ?: return

        // Check if we're already on the full now playing page
        val collapseButton = rootNode.findAccessibilityNodeInfosByViewId("com.google.android.apps.youtube.music:id/player_collapse_button")
        if (collapseButton.isNotEmpty()) {
            Log.d("YTService", "Already on full now playing page, proceeding with share flow")
            proceedWithOriginalShareFlow()
            return
        } else {
            // Not on full page, try to click mini player
            val miniPlayerClicked = clickMiniPlayer(rootNode)
            if (miniPlayerClicked) {
                Toast.makeText(applicationContext, "Mini player clicked, opening now playing...", Toast.LENGTH_SHORT).show()
                // Wait for the now playing page to open, then proceed with share
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    proceedWithOriginalShareFlow()
                }, 1500)
                return
            } else {
                // Mini player not found, proceed normally
                Log.d("YTService", "Mini player not found, proceeding with share flow")
                proceedWithOriginalShareFlow()
            }
        }
    }

    private fun clickMiniPlayer(rootNode: AccessibilityNodeInfo): Boolean {
        try {
            // Look for the mini player at the bottom of the screen
            val miniPlayerNode = findMiniPlayerNode(rootNode)
            if (miniPlayerNode != null) {
                val clicked = miniPlayerNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("YTService", "Mini player click result: $clicked")
                return clicked
            }
        } catch (e: Exception) {
            Log.e("YTService", "Error clicking mini player", e)
        }
        return false
    }

    private fun findMiniPlayerNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Look for nodes that might represent the mini player
        val allNodes = findAllClickableNodes(rootNode)

        for (node in allNodes) {
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val resourceId = node.viewIdResourceName ?: ""

            // Look for common mini player identifiers
            if (contentDesc.contains("now playing") ||
                contentDesc.contains("mini player") ||
                contentDesc.contains("current song") ||
                resourceId.contains("mini_player") ||
                resourceId.contains("now_playing") ||
                resourceId.contains("player_view") ||
                resourceId.contains("bottom_player") ||
                // Look for play/pause buttons in mini player context
                (contentDesc.contains("play") && resourceId.contains("player")) ||
                (contentDesc.contains("pause") && resourceId.contains("player"))) {

                Log.d("YTService", "Found potential mini player: desc='$contentDesc', id='$resourceId'")
                return node
            }
        }

        // Alternative approach: look for nodes at the bottom of screen that might be the mini player
        return findBottomAreaClickable(rootNode)
    }

    private fun findBottomAreaClickable(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val allClickable = findAllClickableNodes(rootNode)

        // Sort by position (nodes lower on screen typically have higher top values)
        val sortedNodes = allClickable.sortedByDescending { node ->
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            rect.top // Sort by vertical position
        }

        // Check the bottom-most clickable nodes that might be the mini player
        for (i in 0..minOf(4, sortedNodes.size - 1)) {
            val node = sortedNodes[i]
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""

            // Skip obvious navigation items
            if (!contentDesc.contains("home") && !contentDesc.contains("search") &&
                !contentDesc.contains("library") && !text.contains("home") &&
                !text.contains("search") && !text.contains("library")) {

                Log.d("YTService", "Trying bottom area clickable $i: desc='$contentDesc', text='$text'")
                return node
            }
        }

        return null
    }

    private fun proceedWithOriginalShareFlow() {
        val rootNode = rootInActiveWindow ?: return
        try {
            val shareNode = findShareButton(rootNode)
            if (shareNode != null) {
                val clicked = shareNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Toast.makeText(applicationContext, "Share button clicked", Toast.LENGTH_SHORT).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        findAndClickShareWithOtherApps()
                    }, 1500)
                } else {
                    Toast.makeText(applicationContext, "Failed to click share button", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(applicationContext, "Share button not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("YTService", "Error in proceedWithOriginalShareFlow", e)
        }
    }

    private fun findShareButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val clickableNodes = findAllClickableNodes(rootNode)

        for (node in clickableNodes) {
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val resourceId = node.viewIdResourceName ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""

            if (contentDesc.contains("share") ||
                contentDesc.contains("menu") ||
                contentDesc.contains("more") ||
                resourceId.contains("share") ||
                resourceId.contains("menu") ||
                resourceId.contains("overflow") ||
                text.contains("share")) {
                return node
            }
        }
        return null
    }

    private fun findAllClickableNodes(node: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val clickableNodes = mutableListOf<AccessibilityNodeInfo>()

        if (node.isClickable && node.isEnabled) {
            clickableNodes.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            clickableNodes.addAll(findAllClickableNodes(child))
        }

        return clickableNodes
    }

    private fun findAndClickShareWithOtherApps() {
        try {
            val newRoot = rootInActiveWindow ?: return

            // First look for "Share with other apps" text
            var shareWithAppsNode = findNodeWithText(newRoot, "Share with other apps")

            if (shareWithAppsNode != null) {
                Log.d("YTService", "Found 'Share with other apps' node")
                Log.d("YTService", "Node clickable: ${shareWithAppsNode.isClickable}, enabled: ${shareWithAppsNode.isEnabled}")

                // Try to find the clickable ancestor
                var clicked = false
                var currentNode: AccessibilityNodeInfo? = shareWithAppsNode
                var level = 0

                while (currentNode != null && !clicked && level < 5) {
                    Log.d("YTService", "Level $level - Clickable: ${currentNode.isClickable}, Enabled: ${currentNode.isEnabled}")

                    if (currentNode.isClickable && currentNode.isEnabled) {
                        clicked = currentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d("YTService", "Click attempt at level $level: $clicked")
                        if (clicked) break
                    }

                    currentNode = currentNode.parent
                    level++
                }

                if (clicked) {
                    Toast.makeText(applicationContext, "Share with other apps clicked!", Toast.LENGTH_SHORT).show()
                } else {
                    Log.d("YTService", "All ancestor click attempts failed")
                    findNearbyClickableForShare(newRoot)
                }
            } else {
                Log.d("YTService", "Share with other apps not found, looking for alternatives")
                findNearbyClickableForShare(newRoot)
            }
        } catch (e: Exception) {
            Log.e("YTService", "Error in findAndClickShareWithOtherApps", e)
        }
    }

    private fun findNearbyClickableForShare(rootNode: AccessibilityNodeInfo) {
        val allClickable = findAllClickableNodes(rootNode)

        Log.d("YTService", "=== Debugging all clickable nodes ===")
        for ((index, node) in allClickable.withIndex()) {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val resourceId = node.viewIdResourceName ?: ""

            Log.d("YTService", "Clickable $index: Text='$text', Desc='$desc', ID='$resourceId'")

            // Look for the main Share button first
            if (desc.lowercase() == "share") {
                Log.d("YTService", "Found main Share button, clicking it")
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Toast.makeText(applicationContext, "Share button clicked, waiting for dialog...", Toast.LENGTH_SHORT).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        findShareWithOtherAppsInDialog()
                    }, 2500)
                    return
                }
            }
        }

        Toast.makeText(applicationContext, "No share button found", Toast.LENGTH_SHORT).show()
    }

    private fun findShareWithOtherAppsInDialog() {
        try {
            val newRoot = rootInActiveWindow ?: return
            Log.d("YTService", "=== Looking for Share with other apps in dialog ===")

            debugAllNodesDetailed(newRoot, 0)

            val searchTexts = listOf(
                "Share with other apps",
                "Other apps",
                "More apps",
                "See all",
                "More options",
                "Show all apps"
            )

            for (searchText in searchTexts) {
                Log.d("YTService", "Searching for: '$searchText'")
                val nodes = newRoot.findAccessibilityNodeInfosByText(searchText)
                if (nodes.isNotEmpty()) {
                    Log.d("YTService", "Found '$searchText' text node")
                    val targetNode = nodes[0]

                    if (tryClickNodeOrParents(targetNode, searchText)) {
                        return
                    }
                }
            }

            Log.d("YTService", "=== Checking all clickable nodes in dialog ===")
            val allClickable = findAllClickableNodes(newRoot)

            for ((index, node) in allClickable.withIndex()) {
                val text = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                val resourceId = node.viewIdResourceName ?: ""

                Log.d("YTService", "Dialog Clickable $index: Text='$text', Desc='$desc', ID='$resourceId'")

                if (text.lowercase().contains("other") || text.lowercase().contains("more") ||
                    text.lowercase().contains("all") || text.lowercase().contains("see") ||
                    desc.lowercase().contains("other") || desc.lowercase().contains("more") ||
                    desc.lowercase().contains("all") || desc.lowercase().contains("see") ||
                    resourceId.contains("more") || resourceId.contains("expand")) {

                    Log.d("YTService", "Attempting to click potential expand option: '$text' / '$desc'")
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (clicked) {
                        Toast.makeText(applicationContext, "Clicked expand option, waiting for full dialog...", Toast.LENGTH_SHORT).show()
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            lookForGreetingCardApp()
                        }, 4000)
                        return
                    }
                }
            }

            if (allClickable.size > 7) {
                val element7 = allClickable[7]
                Log.d("YTService", "Clicking element 7 directly")
                val clicked = element7.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Toast.makeText(applicationContext, "Clicked element 7, checking for expanded dialog...", Toast.LENGTH_SHORT).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        lookForGreetingCardApp()
                    }, 4000)
                    return
                }
            }

            Toast.makeText(applicationContext, "Share with other apps not found in dialog", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("YTService", "Error finding share with other apps in dialog", e)
        }
    }

    private fun tryClickNodeOrParents(targetNode: AccessibilityNodeInfo, searchText: String): Boolean {
        var clicked = false
        var currentNode: AccessibilityNodeInfo? = targetNode
        var level = 0

        while (currentNode != null && !clicked && level < 8) {
            Log.d("YTService", "$searchText Level $level - Clickable: ${currentNode.isClickable}, Enabled: ${currentNode.isEnabled}, Class: ${currentNode.className}")

            if (currentNode.isClickable && currentNode.isEnabled) {
                clicked = currentNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d("YTService", "$searchText Click attempt at level $level: $clicked")
                if (clicked) {
                    Toast.makeText(applicationContext, "$searchText clicked!", Toast.LENGTH_SHORT).show()
                    return true
                }
            }

            currentNode = currentNode.parent
            level++
        }
        return false
    }

    fun lookForGreetingCardApp() {
        try {
            val newRoot = rootInActiveWindow ?: return
            Log.d("YTService", "=== Looking for Greeting Card app in expanded dialog ===")

            val greetingCardNodes = newRoot.findAccessibilityNodeInfosByText("Greeting Card")
            if (greetingCardNodes.isNotEmpty()) {
                val appNode = greetingCardNodes[0]
                if (tryClickNodeOrParents(appNode, "Greeting Card")) {
                    return
                }
            }

            debugAllNodesDetailed(newRoot, 0)
            Toast.makeText(applicationContext, "Greeting Card app not found in share dialog", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e("YTService", "Error looking for greeting card app", e)
        }
    }

    private fun debugAllNodesDetailed(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > 4) return

        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val clickable = node.isClickable
        val className = node.className?.toString() ?: ""
        val resourceId = node.viewIdResourceName ?: ""

        if (text.isNotEmpty() || desc.isNotEmpty() || clickable) {
            Log.d("YTService", "${indent}Node: Text='$text', Desc='$desc', Clickable=$clickable, Class='$className', ID='$resourceId'")
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            debugAllNodesDetailed(child, depth + 1)
        }
    }

    private fun findNodeWithText(rootNode: AccessibilityNodeInfo, targetText: String): AccessibilityNodeInfo? {
        if (rootNode.text?.toString()?.equals(targetText, ignoreCase = true) == true) {
            return rootNode
        }

        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val result = findNodeWithText(child, targetText)
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {
        Log.d("YTService", "Service interrupted")
    }
}
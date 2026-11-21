package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.example.ai.ActionExecutionResult
import com.example.ai.InstalledAppSummary
import com.example.ai.OperatorActionType
import com.example.ai.OperatorStepPlan
import com.example.ai.ScreenNodeInfo
import com.example.ai.ScreenSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

private const val TAG = "OmniAccessibilitySvc"

object AccessibilityBridge {
    private var serviceRef: WeakReference<OmniLiveAccessibilityService>? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _latestSnapshot = MutableStateFlow<ScreenSnapshot?>(null)
    val latestSnapshot: StateFlow<ScreenSnapshot?> = _latestSnapshot.asStateFlow()

    private val _emergencyStopRequested = MutableStateFlow(false)
    val emergencyStopRequested: StateFlow<Boolean> = _emergencyStopRequested.asStateFlow()

    internal fun attach(service: OmniLiveAccessibilityService) {
        serviceRef = WeakReference(service)
        _isConnected.value = true
    }

    internal fun detach() {
        serviceRef = null
        _isConnected.value = false
    }

    internal fun publishSnapshot(snapshot: ScreenSnapshot) {
        _latestSnapshot.value = snapshot
    }

    fun requestEmergencyStop() {
        _emergencyStopRequested.value = true
        hideFloatingHud()
    }

    fun clearEmergencyStop() {
        _emergencyStopRequested.value = false
    }

    fun isServiceEnabledInSettings(context: Context): Boolean {
        if (_isConnected.value && serviceRef?.get() != null) return true
        val expectedId = "${context.packageName}/${OmniLiveAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedId, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun captureScreenNow(): ScreenSnapshot? {
        return serviceRef?.get()?.captureScreenSnapshot()
    }

    suspend fun executePlan(plan: OperatorStepPlan, cachedSnapshot: ScreenSnapshot?): ActionExecutionResult {
        val svc = serviceRef?.get()
            ?: return ActionExecutionResult(false, "ユーザー補助サービスが接続されていません。")
        return svc.executeStep(plan, cachedSnapshot)
    }

    fun showFloatingHud(statusText: String) {
        serviceRef?.get()?.showOrUpdateHud(statusText)
    }

    fun hideFloatingHud() {
        serviceRef?.get()?.removeHud()
    }

    fun queryLaunchableApps(context: Context): List<InstalledAppSummary> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolveInfos
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                val label = info.loadLabel(pm)?.toString().orEmpty().ifBlank { pkg }
                InstalledAppSummary(label = label, packageName = pkg)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}

class OmniLiveAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var hudView: LinearLayout? = null
    private var hudTextView: TextView? = null
    private var windowManager: WindowManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        AccessibilityBridge.attach(this)
        Log.i(TAG, "OmniLiveAccessibilityService connected.")
        captureScreenSnapshot()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Keep snapshot lightweight; only update package if needed or on demand
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "OmniLiveAccessibilityService interrupted.")
    }

    override fun onDestroy() {
        removeHud()
        AccessibilityBridge.detach()
        super.onDestroy()
    }

    fun captureScreenSnapshot(): ScreenSnapshot {
        val metrics = resources.displayMetrics
        val root = rootInActiveWindow
        val collected = mutableListOf<ScreenNodeInfo>()
        var pkg = root?.packageName?.toString().orEmpty()

        if (root != null) {
            traverseNode(root, collected, maxNodes = 40)
            if (pkg.isBlank()) {
                pkg = "unknown"
            }
        }

        val snapshot = ScreenSnapshot(
            packageName = pkg,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
            nodes = collected
        )
        AccessibilityBridge.publishSnapshot(snapshot)
        return snapshot
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        out: MutableList<ScreenNodeInfo>,
        maxNodes: Int
    ) {
        if (out.size >= maxNodes) return

        val rect = Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName?.substringAfterLast("/")?.trim().orEmpty()
        val classSimple = node.className?.toString()?.substringAfterLast(".")?.trim().orEmpty()

        val hasLabel = text.isNotEmpty() || desc.isNotEmpty()
        val isInteractive = node.isClickable || node.isEditable || node.isScrollable

        if (node.isVisibleToUser && (hasLabel || isInteractive) && rect.width() > 0 && rect.height() > 0) {
            out.add(
                ScreenNodeInfo(
                    index = out.size,
                    text = text,
                    contentDescription = desc,
                    viewId = viewId,
                    className = classSimple,
                    bounds = rect,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isChecked = if (node.isCheckable) node.isChecked else null
                )
            )
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            if (out.size >= maxNodes) break
            val child = node.getChild(i) ?: continue
            try {
                traverseNode(child, out, maxNodes)
            } finally {
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
    }

    suspend fun executeStep(plan: OperatorStepPlan, cachedSnapshot: ScreenSnapshot?): ActionExecutionResult {
        return when (plan.actionType) {
            OperatorActionType.TAP_NODE -> tapByNode(plan.targetNodeId, cachedSnapshot)
            OperatorActionType.TAP_COORDS -> {
                if (plan.x < 0 || plan.y < 0) {
                    ActionExecutionResult(false, "無効なタップ座標です (${plan.x}, ${plan.y})")
                } else {
                    val ok = performTapGesture(plan.x.toFloat(), plan.y.toFloat())
                    ActionExecutionResult(ok, if (ok) "座標 (${plan.x}, ${plan.y}) をタップしました。" else "タップジェスチャーに失敗しました。")
                }
            }
            OperatorActionType.INPUT_TEXT -> inputTextOnNode(plan.targetNodeId, plan.inputText, cachedSnapshot)
            OperatorActionType.SCROLL_DOWN -> performScroll(down = true)
            OperatorActionType.SCROLL_UP -> performScroll(down = false)
            OperatorActionType.GLOBAL_BACK -> {
                val ok = performGlobalAction(GLOBAL_ACTION_BACK)
                ActionExecutionResult(ok, if (ok) "戻る操作を実行しました。" else "戻る操作に失敗しました。")
            }
            OperatorActionType.GLOBAL_HOME -> {
                val ok = performGlobalAction(GLOBAL_ACTION_HOME)
                ActionExecutionResult(ok, if (ok) "ホーム画面に移動しました。" else "ホーム操作に失敗しました。")
            }
            OperatorActionType.GLOBAL_RECENTS -> {
                val ok = performGlobalAction(GLOBAL_ACTION_RECENTS)
                ActionExecutionResult(ok, if (ok) "アプリ履歴画面を開きました。" else "履歴操作に失敗しました。")
            }
            OperatorActionType.OPEN_NOTIFICATIONS -> {
                val ok = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                ActionExecutionResult(ok, if (ok) "通知シェードを開きました。" else "通知シェードを開けませんでした。")
            }
            OperatorActionType.OPEN_QUICK_SETTINGS -> {
                val ok = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
                ActionExecutionResult(ok, if (ok) "クイック設定を開きました。" else "クイック設定を開けませんでした。")
            }
            OperatorActionType.LAUNCH_APP -> launchAppByNameOrPackage(plan.inputText)
            OperatorActionType.WAIT -> ActionExecutionResult(true, "画面遷移を待機しました。")
            OperatorActionType.DONE -> ActionExecutionResult(true, "目標の操作が完了しました。")
        }
    }

    private suspend fun tapByNode(nodeIndex: Int, snapshot: ScreenSnapshot?): ActionExecutionResult {
        val target = snapshot?.nodes?.getOrNull(nodeIndex)
            ?: return ActionExecutionResult(false, "指定されたノード [$nodeIndex] が見つかりません。")

        val root = rootInActiveWindow
        if (root != null) {
            val matched = findMatchingNode(root, target)
            if (matched != null) {
                try {
                    if (matched.isClickable && matched.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return ActionExecutionResult(true, "要素 [${target.text.ifBlank { target.contentDescription.ifBlank { target.className } }}] をタップしました。")
                    }
                } finally {
                    @Suppress("DEPRECATION")
                    matched.recycle()
                }
            }
        }

        val cx = target.bounds.centerX().toFloat()
        val cy = target.bounds.centerY().toFloat()
        val gestureOk = performTapGesture(cx, cy)
        return ActionExecutionResult(
            gestureOk,
            if (gestureOk) "要素座標 (${cx.toInt()}, ${cy.toInt()}) をタップしました。" else "ノードタップに失敗しました。"
        )
    }

    private fun inputTextOnNode(nodeIndex: Int, text: String, snapshot: ScreenSnapshot?): ActionExecutionResult {
        val target = snapshot?.nodes?.getOrNull(nodeIndex)
            ?: snapshot?.nodes?.firstOrNull { it.isEditable }
            ?: return ActionExecutionResult(false, "入力可能なテキスト欄が見つかりません。")

        val root = rootInActiveWindow
            ?: return ActionExecutionResult(false, "アクティブウィンドウを取得できません。")

        val matched = findMatchingNode(root, target)
            ?: return ActionExecutionResult(false, "入力対象ノードの再特定に失敗しました。")

        return try {
            matched.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val ok = matched.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            ActionExecutionResult(ok, if (ok) "「$text」を入力しました。" else "テキスト入力に失敗しました。")
        } finally {
            @Suppress("DEPRECATION")
            matched.recycle()
        }
    }

    private fun findMatchingNode(root: AccessibilityNodeInfo, target: ScreenNodeInfo): AccessibilityNodeInfo? {
        val rect = Rect()
        root.getBoundsInScreen(rect)
        if (rect == target.bounds &&
            root.text?.toString()?.trim().orEmpty() == target.text &&
            root.contentDescription?.toString()?.trim().orEmpty() == target.contentDescription
        ) {
            return AccessibilityNodeInfo.obtain(root)
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findMatchingNode(child, target)
            @Suppress("DEPRECATION")
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private suspend fun performTapGesture(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGestureAwait(gesture)
    }

    private suspend fun performScroll(down: Boolean): ActionExecutionResult {
        val metrics = resources.displayMetrics
        val cx = metrics.widthPixels * 0.5f
        val startY = if (down) metrics.heightPixels * 0.72f else metrics.heightPixels * 0.30f
        val endY = if (down) metrics.heightPixels * 0.28f else metrics.heightPixels * 0.74f

        val path = Path().apply {
            moveTo(cx, startY)
            lineTo(cx, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 340L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchGestureAwait(gesture)
        val label = if (down) "下方向へスクロールしました。" else "上方向へスクロールしました。"
        return ActionExecutionResult(ok, if (ok) label else "スクロールジェスチャーに失敗しました。")
    }

    private suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, mainHandler)
            if (!dispatched && cont.isActive) {
                cont.resume(false)
            }
        }

    private fun launchAppByNameOrPackage(query: String): ActionExecutionResult {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return ActionExecutionResult(false, "起動するアプリ名が指定されていません。")

        val apps = AccessibilityBridge.queryLaunchableApps(this)
        val matched = apps.firstOrNull { it.packageName.equals(trimmed, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.equals(trimmed, ignoreCase = true) }
            ?: apps.firstOrNull { it.label.contains(trimmed, ignoreCase = true) || trimmed.contains(it.label, ignoreCase = true) }
            ?: apps.firstOrNull { it.packageName.contains(trimmed, ignoreCase = true) }

        if (matched == null) {
            return ActionExecutionResult(false, "アプリ「$trimmed」が見つかりませんでした。")
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(matched.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return ActionExecutionResult(false, "${matched.label} の起動インテントを取得できません。")

        return try {
            startActivity(launchIntent)
            ActionExecutionResult(true, "アプリ「${matched.label}」を起動しました。")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch app ${matched.packageName}", e)
            ActionExecutionResult(false, "アプリ起動エラー: ${e.localizedMessage}")
        }
    }

    fun showOrUpdateHud(statusText: String) {
        mainHandler.post {
            val wm = windowManager ?: return@post
            if (hudView == null) {
                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(28, 16, 20, 16)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#E60E1422"))
                        cornerRadius = 48f
                        setStroke(2, Color.parseColor("#38BDF8"))
                    }
                }
                val label = TextView(this).apply {
                    setTextColor(Color.parseColor("#F1F5F9"))
                    textSize = 13f
                    maxLines = 2
                    text = statusText
                }
                val stopBtn = TextView(this).apply {
                    setTextColor(Color.parseColor("#FCA5A5"))
                    textSize = 12f
                    setPadding(24, 8, 12, 8)
                    text = "■ 停止"
                    setOnClickListener {
                        AccessibilityBridge.requestEmergencyStop()
                    }
                }
                container.addView(label)
                container.addView(stopBtn)

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = 96
                }
                try {
                    wm.addView(container, params)
                    hudView = container
                    hudTextView = label
                } catch (e: Exception) {
                    Log.e(TAG, "Could not attach accessibility overlay HUD", e)
                }
            } else {
                hudTextView?.text = statusText
            }
        }
    }

    fun removeHud() {
        mainHandler.post {
            val view = hudView ?: return@post
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
            } finally {
                hudView = null
                hudTextView = null
            }
        }
    }
}

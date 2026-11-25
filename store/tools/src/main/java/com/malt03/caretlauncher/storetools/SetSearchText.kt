package com.malt03.caretlauncher.storetools

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Puts text into the launcher's search field, since `adb shell input text` types only ASCII:
 * `am instrument -w -e text64 <base64 of the UTF-8 text> com.malt03.caretlauncher.storetools/.SetSearchText`.
 * It instruments its own package, so the launcher's process keeps running.
 */
class SetSearchText : Instrumentation() {
    private lateinit var text: String

    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)
        text = String(Base64.decode(arguments.getString("text64").orEmpty(), Base64.DEFAULT), Charsets.UTF_8)
        start()
    }

    override fun onStart() {
        val automation = uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        var field: AccessibilityNodeInfo? = null
        val deadline = System.currentTimeMillis() + 10_000
        while (field == null && System.currentTimeMillis() < deadline) {
            field = automation.windows.firstNotNullOfOrNull { window -> window.root?.let(::findEditable) }
            if (field == null) Thread.sleep(200)
        }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = field?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments) == true
        finish(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString(REPORT_KEY_STREAMRESULT, if (ok) "set\n" else "no search field\n")
        })
    }

    /** The first editable node under [node] (the launcher has one field: the search field). */
    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let(::findEditable)?.let { return it }
        }
        return null
    }
}

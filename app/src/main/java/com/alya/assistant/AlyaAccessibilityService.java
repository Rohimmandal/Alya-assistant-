package com.alya.assistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

public final class AlyaAccessibilityService extends AccessibilityService {
    private static volatile AlyaAccessibilityService instance;

    static AlyaAccessibilityService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    String readVisibleText() {
        AccessibilityNodeInfo root = findOtherApplicationRoot();
        if (root == null) return "No readable text from another app is currently visible.";
        StringBuilder result = new StringBuilder();
        appendText(root, result);
        root.recycle();
        return result.length() == 0 ? "No readable text from another app is currently visible." : result.toString();
    }

    boolean clickVisibleText(String requestedText) {
        AccessibilityNodeInfo root = findOtherApplicationRoot();
        if (root == null) return false;
        boolean clicked = clickText(root, requestedText.toLowerCase(java.util.Locale.ROOT));
        root.recycle();
        return clicked;
    }

    boolean goBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    boolean goHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private AccessibilityNodeInfo findOtherApplicationRoot() {
        for (AccessibilityWindowInfo window : getWindows()) {
            if (window.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            AccessibilityNodeInfo root = window.getRoot();
            if (root == null) continue;
            CharSequence packageName = root.getPackageName();
            if (packageName != null && !getPackageName().contentEquals(packageName)) return root;
            root.recycle();
        }
        return null;
    }

    private void appendText(AccessibilityNodeInfo node, StringBuilder result) {
        if (node == null || result.length() >= 3000 || node.isPassword()) return;
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        appendValue(result, text);
        appendValue(result, description);
        for (int index = 0; index < node.getChildCount() && result.length() < 3000; index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                appendText(child, result);
                child.recycle();
            }
        }
    }

    private void appendValue(StringBuilder result, CharSequence value) {
        if (value == null || value.length() == 0 || result.length() >= 3000) return;
        String text = value.toString().trim();
        if (!text.isEmpty() && !result.toString().contains(text)) result.append(text).append('\n');
    }

    private boolean clickText(AccessibilityNodeInfo node, String requestedText) {
        if (node == null || node.isPassword()) return false;
        CharSequence label = node.getText();
        CharSequence description = node.getContentDescription();
        boolean matches = label != null && label.toString().toLowerCase(java.util.Locale.ROOT).equals(requestedText)
                || description != null && description.toString().toLowerCase(java.util.Locale.ROOT).equals(requestedText);
        if (matches) {
            AccessibilityNodeInfo target = node;
            while (target != null && !target.isClickable()) {
                AccessibilityNodeInfo parent = target.getParent();
                if (target != node) target.recycle();
                target = parent;
            }
            if (target != null) {
                boolean result = target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (target != node) target.recycle();
                return result;
            }
        }
        for (int index = 0; index < node.getChildCount(); index++) {
            AccessibilityNodeInfo child = node.getChild(index);
            if (child != null) {
                boolean clicked = clickText(child, requestedText);
                child.recycle();
                if (clicked) return true;
            }
        }
        return false;
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
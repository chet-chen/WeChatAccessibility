package com.demo.wechataccessibility;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by agentchen on 2016/12/1.
 * Email agentchen97@gmail.com
 */

public class MyAccessibilityService extends AccessibilityService {
    private static final String TAG = "MyAccessibilityService";
    private static final String OPEN_BUTTON_ID = "com.tencent.mm:id/bi3";
    private static final String BACK_BUTTON_ID = "com.tencent.mm:id/gv";
    private static final String SLOW_TEXT_ID = "com.tencent.mm:id/bg6";
    private boolean isAccessibility = false;
    private boolean isMeUnLock = false;
    private PowerManager pm;
    private PowerManager.WakeLock wakeLock;
    private KeyguardManager km;
    private KeyguardManager.KeyguardLock keyguardLock;
    private List<AccessibilityNodeInfo> parents;
    private long timeOut;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        parents = new ArrayList<>();
        pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "bright");
        keyguardLock = km.newKeyguardLock("");
        timeOut = getTimeOut();
        Log.d(TAG, "onServiceConnected:  timeOut: " + timeOut);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        switch (eventType) {
//          通知栏发生变化
            case AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED:
                List<CharSequence> texts = event.getText();
                if (!texts.isEmpty()) {
                    for (CharSequence text : texts) {
                        String content = text.toString();
                        Log.d(TAG, "onAccessibilityEvent: Notification " + content);
                        if (content.contains("[微信红包]")) {
                            if (event.getParcelableData() != null &&
                                    event.getParcelableData() instanceof Notification) {
                                Notification notification = (Notification) event.getParcelableData();
                                PendingIntent pendingIntent = notification.contentIntent;
                                try {
                                    isAccessibility = true;
                                    Log.d(TAG, "onAccessibilityEvent: start Accessibility");
                                    autoUnlock();
                                    pendingIntent.send();
//                                  如果开启抢红包之前就处在LauncherUI则不会回调WindowChange,
//                                  2秒后手动getLastPacket();如果之前不处在LauncherUI，
//                                  pendingIntent.send()之后会回调onAccessibilityEvent，
//                                  2秒sleep之后就执行不到。（why？）
                                    try {
                                        Thread.sleep(2000);
                                        Log.d(TAG, "onAccessibilityEvent: 手动getLastPacket()");
                                        getLastPacket();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                } catch (PendingIntent.CanceledException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }
                break;
//            窗口状态发生变化
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                Log.d(TAG, "onAccessibilityEvent: TYPE_WINDOWS_CHANGED" + event.getClassName().toString());
                if (!isAccessibility) {
                    return;
                }
                String className = event.getClassName().toString();
                switch (className) {
                    case "com.tencent.mm.ui.LauncherUI":
                        Log.d(TAG, "自动 getLastPacket()");
                        getLastPacket();
                        break;
                    case "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI":
                        inputClick(OPEN_BUTTON_ID);
                        Log.d(TAG, "onAccessibilityEvent: 开红包");
                        slow(SLOW_TEXT_ID);//手慢了
                        break;
                    case "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI":
                        inputClick(BACK_BUTTON_ID);
                        Log.d(TAG, "onAccessibilityEvent: 退出红包");
                        isAccessibility = false;
                        doBack();
                        autoLock();
                        Log.d(TAG, "onAccessibilityEvent: stop Accessibility");
                        break;
                    default:
                        break;
                }
                break;
        }
    }

    @Override
    public void onInterrupt() {

    }

    private void inputClick(String clickId) {
//        点击指定id Node
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(clickId);
            for (AccessibilityNodeInfo accessibilityNodeInfo : list) {
                Log.d(TAG, "inputClick: " + accessibilityNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            }
        }
    }

    public void getLastPacket() {
//        点击最后一个红包
        try {
            Thread.sleep(timeOut);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "getLastPacket: 进入微信");
        AccessibilityNodeInfo rootNode = getRootInActiveWindow();
        parents.clear();
        recycle(rootNode);
        if (parents.size() > 0) {
            parents.get(parents.size() - 1).performAction(AccessibilityNodeInfo.ACTION_CLICK);
            Log.d(TAG, "onAccessibilityEvent: 点击最后一个红包");
        } else {
            Log.d(TAG, "getLastPacket: 没有红包");
            doBack();
            isAccessibility = false;
            autoLock();
        }
    }

    private void recycle(AccessibilityNodeInfo rootNode) {
//        把本页面红包全部存进 parents list
        if (rootNode.getChildCount() == 0) {
            if (rootNode.getText() != null) {
                if ("领取红包".equals(rootNode.getText().toString())) {
                    if (rootNode.isClickable()) {
                        rootNode.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    }
                    AccessibilityNodeInfo parent = rootNode.getParent();
                    while (parent != null) {
                        if (parent.isClickable()) {
                            parents.add(parent);
                            Log.d(TAG, "recycle: list add parent");
                            break;
                        }
                        parent = parent.getParent();
                    }
                }
            }
        } else {
            for (int i = 0; i < rootNode.getChildCount(); i++) {
                if (rootNode.getChild(i) != null) {
                    recycle(rootNode.getChild(i));
                }
            }
        }
    }

    private void slow(String id) {
//        手慢了情况下
        AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
        if (nodeInfo != null) {
            List<AccessibilityNodeInfo> list = nodeInfo.findAccessibilityNodeInfosByViewId(id);
            for (AccessibilityNodeInfo accessibilityNodeInfo : list) {
                String s = accessibilityNodeInfo.getText().toString();
                if (s.contains("手慢了")) {
                    Log.d(TAG, "slow: 手慢了");
                    doBack();
                    doBack();
                    autoLock();
                    isAccessibility = false;
                    Log.d(TAG, "slow: stop Accessibilty");
                }
            }
        }
    }

    private void autoUnlock() {
        if (!pm.isScreenOn()) {
            wakeLock.acquire();
            Log.d(TAG, "onAccessibilityEvent: 亮屏");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (km.isDeviceLocked()) {
                Log.d(TAG, "autoUnlock: sdk >= 22: 屏幕被密码锁柱");
                wakeLock.release();
            } else {
                if (km.inKeyguardRestrictedInputMode()) {
                    keyguardLock.disableKeyguard();
                    Log.d(TAG, "onAccessibilityEvent: 尝试解锁");
                    isMeUnLock = true;
                }
            }
        }
    }

    private void autoLock() {
        if (isMeUnLock) {
            try {
                Thread.sleep(timeOut);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            keyguardLock.reenableKeyguard();
            Log.d(TAG, "autoLock: 自动锁");
            if (wakeLock != null && pm.isScreenOn()) {
                wakeLock.release();
                Log.d(TAG, "autoLock: 自动灭");
            }
            isMeUnLock = false;
        }
    }

    private void doBack() {
        try {
            Thread.sleep(timeOut);
            this.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        } catch (InterruptedException e) {
            e.printStackTrace();
            isAccessibility = false;
        }
    }

    private long getTimeOut() {
        SharedPreferences sp = getSharedPreferences("timeOut", MODE_PRIVATE);
        long timeOUt = sp.getLong("timeOut", 0);
        return timeOUt > 0 ? timeOUt : 500;
    }
}
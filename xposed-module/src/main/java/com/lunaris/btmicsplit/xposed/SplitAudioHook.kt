package com.lunaris.btmicsplit.xposed

import android.media.AudioManager
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class SplitAudioHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "BtMicSplit"
        private const val PROP = "persist.lunaris.btmicsplit"
    }

    /**
     * Reads the toggle via reflection on the hidden android.os.SystemProperties
     * class. Works from any process — no special permission needed to *read*
     * a persist. property.
     */
    private fun isSplitEnabled(): Boolean {
        return try {
            val cls = Class.forName("android.os.SystemProperties")
            val get = cls.getMethod("get", String::class.java, String::class.java)
            (get.invoke(null, PROP, "0") as String) == "1"
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: failed to read $PROP: $t")
            false
        }
    }

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        hookAudioManagerSco(lpparam)
        hookBluetoothHeadsetSco(lpparam)
        hookTelecomIntegration(lpparam)
    }

    private fun hookAudioManagerSco(lpparam: LoadPackageParam) {
        val blockingReplacement = object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                return if (isSplitEnabled()) {
                    XposedBridge.log("$TAG: blocked ${param.method.name} from ${lpparam.packageName}")
                    null
                } else {
                    XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                }
            }
        }

        tryHook(AudioManager::class.java, "startBluetoothSco", blockingReplacement)
        tryHook(AudioManager::class.java, "startBluetoothScoVirtualCall", blockingReplacement)
    }

    private fun hookBluetoothHeadsetSco(lpparam: LoadPackageParam) {
        try {
            val btHeadsetCls = XposedHelpers.findClass(
                "android.bluetooth.BluetoothHeadset",
                lpparam.classLoader
            )
            tryHook(btHeadsetCls, "startScoUsingVirtualVoiceCall", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isSplitEnabled()) {
                        XposedBridge.log("$TAG: blocked BluetoothHeadset.startScoUsingVirtualVoiceCall from ${lpparam.packageName}")
                        param.result = false
                    }
                }
            })
        } catch (t: Throwable) {
            // BluetoothHeadset not loaded in this process — expected for most apps.
        }
    }

    /**
     * Discord registers itself with Telecom as a self-managed ConnectionService,
     * which is what pulls Bluetooth SCO in for its calls (confirmed via logcat:
     * CallAudioRouteController managing the route). Blocking Discord's Telecom
     * calls stops that integration — voice chat itself is separate WebRTC audio
     * and shouldn't be affected, but Discord's own call notification/Bluetooth
     * media-button controls may stop working while this is on. Scoped to
     * Discord only, as a safety measure against affecting other apps.
     */
    private fun hookTelecomIntegration(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.discord") return

        try {
            val telecomCls = XposedHelpers.findClass(
                "android.telecom.TelecomManager", lpparam.classLoader
            )
            val blockingReplacement = object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    return if (isSplitEnabled()) {
                        XposedBridge.log("$TAG: blocked Telecom.${param.method.name} from Discord")
                        null
                    } else {
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                    }
                }
            }
            tryHook(telecomCls, "placeCall", blockingReplacement)
            tryHook(telecomCls, "addNewIncomingCall", blockingReplacement)
            tryHook(telecomCls, "registerPhoneAccount", blockingReplacement)
        } catch (t: Throwable) {
            // TelecomManager not loaded in this process, or class not found — skip quietly.
        }
    }

    private fun tryHook(cls: Class<*>, methodName: String, hook: XC_MethodHook) {
        try {
            XposedHelpers.findAndHookMethod(cls, methodName, hook)
        } catch (t: Throwable) {
            // Method not present on this API level/vendor build — skip quietly.
        }
    }
}

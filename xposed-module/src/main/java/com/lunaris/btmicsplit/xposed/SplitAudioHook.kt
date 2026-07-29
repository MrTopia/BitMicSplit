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
        // System UI / system_server aren't reached via handleLoadPackage in the
        // typical Zygisk/LSPosed all-apps scope config; this hooks every regular
        // app process, which is where AudioManager/BluetoothHeadset calls
        // requesting SCO actually originate.

        hookAudioManagerSco(lpparam)
        hookBluetoothHeadsetSco(lpparam)
    }

    private fun hookAudioManagerSco(lpparam: LoadPackageParam) {
        val blockingReplacement = object : XC_MethodReplacement() {
            override fun replaceHookedMethod(param: MethodHookParam): Any? {
                return if (isSplitEnabled()) {
                    XposedBridge.log("$TAG: blocked ${param.method.name} from ${lpparam.packageName}")
                    null // no-op: never bring up the SCO link
                } else {
                    XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                }
            }
        }

        // Public API present since API 1.
        tryHook(AudioManager::class.java, "startBluetoothSco", blockingReplacement)

        // Hidden/internal API used for VoIP-style virtual calls — present on
        // most AOSP-derived versions but guard in case a vendor tree renamed
        // or removed it.
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

    private fun tryHook(cls: Class<*>, methodName: String, hook: XC_MethodHook) {
        try {
            XposedHelpers.findAndHookMethod(cls, methodName, hook)
        } catch (t: Throwable) {
            // Method not present on this API level/vendor build — skip quietly.
        }
    }
}

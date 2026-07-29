# BT Mic Split — QS toggle for "BT audio out / phone mic in"

Two pieces, built as two separate Android modules:

1. **`qs-tile-app/`** — a tiny app with one Quick Settings tile. Tapping it
   flips a persisted system property with root:
   `persist.lunaris.btmicsplit` = `1` (on) / `0` (off).
2. **`xposed-module/`** — an LSPosed (Zygisk) module. It hooks the calls apps
   use to request a Bluetooth SCO link (`AudioManager.startBluetoothSco()`,
   `AudioManager.startBluetoothScoVirtualCall()`,
   `BluetoothHeadset.startScoUsingVirtualVoiceCall()`) and no-ops them
   whenever the property is `1`. No SCO link ever comes up, so
   AudioPolicyManager falls back to the built-in mic for capture — while
   A2DP media output keeps routing to the BT earphones untouched, since
   that's a completely separate audio path from SCO.

Nothing here patches the audio HAL or AudioPolicyManager itself. It's a
pure app-layer choke point, which is why it's portable across ROMs — you
need LSPosed (Zygisk-based, e.g. via Magisk + the LSPosed zygisk module)
installed, and root for the QS tile to set the property.

## Build

Both are standard Gradle Android modules — open each folder in Android
Studio (or add as modules to one project with a shared root
`settings.gradle`), build the APKs normally. Nothing exotic in
dependencies beyond the Xposed API (`de.robv.android.xposed:api:82`,
compileOnly — matches what LSPosed implements) for the hook module.

## Install

1. Install `xposed-module` APK, enable it in LSPosed Manager, scope =
   **all apps** (the "system framework"/system_server scope is *not*
   enough here — the hook target methods run in the calling app's own
   process, not system_server, so scope needs to include whichever apps
   you want this to affect, or just select all).
2. Reboot (Zygisk modules apply at next zygote start).
3. Install `qs-tile-app` APK, grant it root when prompted, add the tile
   to your Quick Settings panel.

## Important limitation — read before relying on this for calls

This affects **VoIP/media apps that use separate `AudioRecord`/mic capture
and media-output streams** — WhatsApp/Discord/Zoom calls, voice memo
apps recording while music plays, etc.

It does **not** split a native cellular phone call. HFP bundles TX+RX
into a single SCO link by protocol design — a real phone call's mic and
earpiece audio can't be routed to two different devices, root or not.
If you enable the toggle during a real phone call over a BT headset,
expect the call to just fall back to phone mic *and* phone
earpiece/speaker, not phone mic + BT speaker.

## Why not a framework/AOSP patch instead

Since you're already building Lunaris, the "correct" long-term home for
this is a small patch in `frameworks/base` (`AudioDeviceBroker`/`BtHelper`,
gate the same startBluetoothSco path behind a `Settings.Global` flag) plus
a SystemUI QS tile — no LSPosed dependency, works even without
Zygisk/root at boot. Happy to spec that version out instead if you want
it baked into the ROM later; this standalone version was scoped as
requested so it also works on stock/other ROMs.
# BitMicSplit
# BitMicSplit

# Place the built qs-tile-app release APK at
# magisk-module/system/priv-app/BtMicSplit/BtMicSplit.apk before zipping,
# then flash this in Magisk Manager. Installing as a priv-app means the
# tile gets root silently via its own UID instead of prompting Superuser
# each time (still requires it be granted root/whitelisted once in your
# root manager's priv-app allowlist, depending on which root solution
# you use).

set_perm_recursive $MODPATH/system/priv-app 0 0 0755 0644
set_perm $MODPATH/system/priv-app/BtMicSplit/BtMicSplit.apk 0 0 0644

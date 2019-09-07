#!/system/bin/sh
# Author: cjybyjk @ coolapk

APK_FILE="$1"
APK_TMP="/data/local/tmp/tmp_$(date +%s).apk"
echo "Copying apk file..."
cp "$APK_FILE" "$APK_TMP"
echo "Installing apk..."
pm install -r --user 0 "$APK_TMP"
echo "Cleaning files..."
rm -f "$APK_TMP"
echo "Done!"
exit 0

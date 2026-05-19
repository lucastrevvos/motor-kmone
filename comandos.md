./gradlew.bat :app:installDebug

adb logcat -c

adb logcat -v time | Select-String "KM_V2_ECONOMIC|KM_V2_DECISION_PRESENTATION|KM_V2_PARSER|KM_V2_DEDUPE|KM_V2_OCR"

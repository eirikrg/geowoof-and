# App-specific R8 rules. Libraries ship their own consumer rules (Compose, Navigation, Hilt,
# kotlinx.serialization); add rules here only for code that R8 cannot see (reflection, JNI).

# SignalR writes its protocol messages with Gson reflection.
-keep class com.microsoft.signalr.** { *; }
-dontwarn org.slf4j.**

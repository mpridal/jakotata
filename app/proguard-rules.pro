# JNI: metody volané z C++ nepřejmenovávat.
-keepclasseswithmembernames class * { native <methods>; }

# MediaPipe Tasks: grafy a kalkulátory se načítají reflexí a přes protobuf (R8 by je odstranil).
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
# Flogger (logování MediaPipe) hledá volající třídu na zásobníku podle jména – přejmenování
# způsobí pád „no caller found on the stack“ při prvním vytvoření grafu (v0.1.0).
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**

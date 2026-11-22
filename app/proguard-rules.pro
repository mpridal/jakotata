# JNI: metody volané z C++ nepřejmenovávat.
-keepclasseswithmembernames class * { native <methods>; }

# MediaPipe Tasks: grafy a kalkulátory se načítají reflexí a přes protobuf (R8 by je odstranil).
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**

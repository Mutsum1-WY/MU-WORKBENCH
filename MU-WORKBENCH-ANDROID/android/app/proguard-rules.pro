# Preserve line numbers for readable stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations

# Android components, services, receivers & WorkManager workers
-keep public class com.mu.workbench.MainActivity { *; }
-keep public class com.mu.workbench.KeepAliveService { *; }
-keep public class com.mu.workbench.TodoAlarmReceiver { *; }
-keep public class com.mu.workbench.BgCheckWorker { *; }
-keep class com.mu.workbench.BgCheck { *; }
-keep class com.mu.workbench.MuBgCheck { *; }
-keep class com.mu.workbench.MuKeepAlive { *; }
-keep class com.mu.workbench.MuNative { *; }
-keep class com.mu.workbench.PortalLogin { *; }

# Keep WorkManager worker constructors
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# CameraX / ZXing / JSON safety
-keep class androidx.camera.** { *; }
-keep class com.google.zxing.** { *; }
-keep class org.json.** { *; }

# Harmless R8 warnings in Java 8+/JVM libs
-dontwarn java.lang.invoke.StringConcatFactory
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn javax.annotation.**

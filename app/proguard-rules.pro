# Project Model
-keep class com.inkframe.core.model.** { *; }

# Prevent shrinking of Enums used in JSON (BlendMode)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Compose rules
-keep class androidx.compose.ui.platform.** { *; }
-keep class androidx.compose.runtime.** { *; }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Annotations / reflection ---
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# --- Kotlin ---
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

-keep class com.step.counter.core.data.** { *; }
-keep class com.step.counter.core.domain.** { *; }

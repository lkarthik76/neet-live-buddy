# Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.smartstudybuddy.app.**$$serializer { *; }
-keepclassmembers class com.smartstudybuddy.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.smartstudybuddy.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes used in JSON serialization
-keep class com.smartstudybuddy.app.TutorRequest { *; }
-keep class com.smartstudybuddy.app.TutorResponse { *; }
-keep class com.smartstudybuddy.app.RevisionCard { *; }
-keep class com.smartstudybuddy.app.OptionAnalysis { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

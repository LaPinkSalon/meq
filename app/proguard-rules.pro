# MEQ Colour Checker - ProGuard Rules
# This file contains ProGuard rules for the MEQ Colour Checker app.
# R8 optimization is enabled to reduce APK size and improve performance.

# ================================
# General Android Rules
# ================================

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom views
-keepclassmembers class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ================================
# Hilt / Dagger Rules
# ================================

# Keep Hilt generated classes
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Hilt Android entry points
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# Keep Hilt ViewModels
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep all classes that use @Inject
-keepclasseswithmembers class * {
    @javax.inject.Inject <init>(...);
}
-keepclasseswithmembers class * {
    @javax.inject.Inject <fields>;
}

# Keep Dagger generated classes
-keep class **_Factory { *; }
-keep class **_MembersInjector { *; }
-keep class **_HiltModules** { *; }
-keep class **Hilt_** { *; }

# ================================
# OpenCV / JavaCPP Rules
# ================================

# Keep all OpenCV classes
-keep class org.bytedeco.** { *; }
-keep class org.opencv.** { *; }
-keepclassmembers class org.bytedeco.** { *; }

# Keep JavaCPP Loader
-keep class org.bytedeco.javacpp.Loader { *; }
-keep class org.bytedeco.javacpp.annotation.** { *; }

# Keep native OpenCV bindings
-keep class org.bytedeco.opencv.global.** { *; }
-keep class org.bytedeco.opencv.opencv_core.** { *; }
-keep class org.bytedeco.opencv.opencv_imgproc.** { *; }

# Don't warn about JavaCPP
-dontwarn org.bytedeco.**
-dontwarn org.opencv.**

# Keep indexers for OpenCV
-keep class org.bytedeco.javacpp.indexer.** { *; }

# ================================
# Kotlin Rules
# ================================

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep Kotlin coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Don't optimize away coroutine debug info
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }

# Keep coroutine internal frame class
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# ================================
# Jetpack Compose Rules
# ================================

# Keep all Composable functions
-keep @androidx.compose.runtime.Composable class * { *; }
-keep @androidx.compose.runtime.Composable interface * { *; }

# Keep Compose compiler annotations
-keep class androidx.compose.runtime.** { *; }
-keepclassmembers class androidx.compose.** { *; }

# Keep CompositionLocal
-keep class androidx.compose.runtime.CompositionLocal { *; }

# ================================
# Timber Logging
# ================================

# Keep Timber
-keep class timber.log.** { *; }
-keepclassmembers class timber.log.** { *; }

# ================================
# App-Specific Rules
# ================================

# Keep our domain models (used in sealed classes)
-keep class com.meq.colourchecker.domain.** { *; }
-keep class com.meq.colourchecker.processing.** { *; }

# Keep UI models
-keep class com.meq.colourchecker.ui.model.** { *; }

# Keep error classes (for error handling)
-keep class com.meq.colourchecker.domain.DetectionError { *; }
-keep class com.meq.colourchecker.domain.DetectionError$* { *; }

# Keep sealed classes
-keep class * extends com.meq.colourchecker.domain.DetectionResult { *; }

# Keep data classes used in state
-keep @kotlin.Metadata class com.meq.colourchecker.** {
    <fields>;
    <init>(...);
    public *** component1();
    public *** component2();
    public *** component3();
    public *** component4();
    public *** component5();
    public *** component6();
    public *** copy(...);
}

# Keep Logger interface and implementations
-keep interface com.meq.colourchecker.util.Logger { *; }
-keep class com.meq.colourchecker.util.TimberLogger { *; }
-keep class com.meq.colourchecker.util.NoOpLogger { *; }

# Keep detector interface
-keep interface com.meq.colourchecker.processing.ColorCheckerDetector { *; }

# ================================
# AndroidX & Material Rules
# ================================

# Keep AndroidX components
-keep class androidx.lifecycle.** { *; }
-keep class androidx.compose.** { *; }

# Keep Material 3 components
-keep class com.google.android.material.** { *; }

# ================================
# Reflection Rules
# ================================

# Keep classes used via reflection
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep source file names and line numbers for better stack traces
-keepattributes SourceFile,LineNumberTable

# Rename source file attribute to hide actual source file name
-renamesourcefileattribute SourceFile

# ================================
# Optimization Rules
# ================================

# Enable aggressive optimization
-optimizationpasses 5
-dontpreverify
-allowaccessmodification

# Repackage classes into single package
-repackageclasses 'com.meq.colourchecker'

# ================================
# Warnings to Ignore
# ================================

# Ignore warnings from third-party libraries
-dontwarn org.bytedeco.javacpp.**
-dontwarn org.bytedeco.opencv.**
-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**

# ================================
# Debugging (Remove in production)
# ================================

# Print mapping for debugging
-printmapping build/outputs/mapping/release/mapping.txt

# Keep original names for debugging (comment out for production)
# -dontobfuscate

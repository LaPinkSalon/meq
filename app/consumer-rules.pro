# Consumer ProGuard Rules for MEQ Colour Checker
# These rules are automatically applied to consumers of this module

# Keep OpenCV native bindings
-keep class org.bytedeco.** { *; }
-keep class org.bytedeco.javacpp.** { *; }

# Keep our public API
-keep public class com.meq.colourchecker.processing.ColorCheckerDetector { *; }
-keep public class com.meq.colourchecker.processing.DetectorResult { *; }
-keep public class com.meq.colourchecker.processing.AnalysisFrame { *; }

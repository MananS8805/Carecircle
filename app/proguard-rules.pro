# R8 rules for the release build. Hand-checked: the libraries below ship their own consumer rules (Retrofit, OkHttp, Room,
# WorkManager, CameraX, ML Kit, Firebase, Compose), so only what CareCircle itself needs by reflection is listed. There is
# deliberately no blanket keep of com.carecircle.**: the app is shrunk and obfuscated like any other.
#
# Manifest components (activities, receivers, services) and WorkManager workers are kept automatically: AAPT2 generates
# rules for the manifest, and WorkManager keeps ListenableWorker subclasses.

# Readable crash traces from the mapping file.
-keepattributes SourceFile,LineNumberTable,*Annotation*,InnerClasses,Signature,Exceptions
-renamesourcefileattribute SourceFile

# ---- kotlinx.serialization (backup file, API payloads, saved policy JSON) ----
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.carecircle.**$$serializer { *; }
-keepclassmembers class com.carecircle.** { *** Companion; }
-keepclasseswithmembers class com.carecircle.** { kotlinx.serialization.KSerializer serializer(...); }

# ---- Retrofit ----
# The converter finds serializers for CareApi's return and body types by reflection; Retrofit's own consumer rules keep the
# annotated interface methods and the suspend Continuation signature.
-keep interface com.carecircle.data.CareApi

# ---- Room ----
# Generated *_Impl classes are kept by Room's consumer rules; entities are only touched through them.

# ---- Firebase and Google services ----
# Firebase, Play services and ML Kit ship consumer rules. If R8 reports a missing class from them, add a -dontwarn for it here
# after checking the class really is optional; do not keep whole packages.

# ---- Optional classes that OkHttp mentions but Android does not have ----
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# ---- Debug-only hook (phase E) must never survive into release ----
# It lives only in the debug source set, so nothing is kept here on purpose.

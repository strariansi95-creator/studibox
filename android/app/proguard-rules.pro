# ---------------------------------------------------------------------------
# kotlinx.serialization
# Без этих правил Release-сборка падает с
# "SerializationException: Serializer has not been found for type ...".
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault

-dontnote kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Сгенерированные компилятором сериализаторы приложения
-keep,includedescriptorclasses class com.studibox.app.**$$serializer { *; }
-keepclassmembers class com.studibox.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.studibox.app.data.remote.** { *; }

# ---------------------------------------------------------------------------
# Retrofit / OkHttp
# ---------------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep interface com.studibox.app.data.remote.ScheduleApi { *; }

# ---------------------------------------------------------------------------
# Room
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class com.studibox.app.data.local.** { *; }
-dontwarn androidx.room.paging.**

# ---------------------------------------------------------------------------
# WorkManager
# ---------------------------------------------------------------------------
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

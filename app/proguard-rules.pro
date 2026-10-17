# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hustlecity.game.**$$serializer { *; }
-keepclassmembers class com.hustlecity.game.** { *** Companion; }
-keepclasseswithmembers class com.hustlecity.game.** { kotlinx.serialization.KSerializer serializer(...); }

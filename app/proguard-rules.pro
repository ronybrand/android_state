# kotlinx.serialization: the generated serializers are found by name, keep them.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.ronybrand.estados.**$$serializer { *; }
-keepclassmembers class com.ronybrand.estados.** { *** Companion; }
-keepclasseswithmembers class com.ronybrand.estados.** { kotlinx.serialization.KSerializer serializer(...); }

# Room
-keep class androidx.room.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { public **[] *; }

# Hilt / Dagger generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep backup DTOs used by manual JSON serialization
-keep class com.moneyplanner.data.backup.** { *; }

# Kotlin metadata
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

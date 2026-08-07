# --- Silenciar warnings de AWT/JNA (Android não tem AWT)
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn com.sun.jna.**
-dontwarn com.sun.jna.platform.**
-dontwarn java.lang.invoke.**

# Mantém a API JNA usada (evita que R8 remova classes JNA que você precisa)
-keep class com.sun.jna.** { *; }
-keep class com.sun.jna.platform.** { *; }

# Evita que R8 gere erros por classes opcionais de desktop
-dontnote com.sun.jna.**

# =======================
# JNI / NATIVE
# =======================
-keep class * {
    native <methods>;
}

# =======================
# Kotlin Serialization
# =======================
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

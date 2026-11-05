# Aturan R8/ProGuard - KonveksiPro Stage 2
# Catatan: kripto lisensi (Stage 10) memakai Tink, bukan java.security Ed25519
# (Ed25519 platform baru tersedia mulai API 33 - lihat laporan audit Stage 0).

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Komponen AI: nama kelas dipertahankan agar stack trace diagnostik tetap terbaca.
# CATATAN: ini BUKAN perlindungan kredensial. API key tetap dapat diekstrak dari APK.
-keep class com.dooyappwork.konveksipro.ai.** { *; }
-keepclassmembers class com.dooyappwork.konveksipro.ai.** { *; }

# Jangan pernah mencetak BuildConfig ke log (dijaga oleh kode, bukan R8).
-keep class com.dooyappwork.konveksipro.BuildConfig { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**

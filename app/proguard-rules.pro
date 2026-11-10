# Widget providers and activity are referenced from the manifest and kept automatically.
-dontwarn org.xmlpull.v1.**

# "Book with highlights" export
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
-dontwarn com.google.re2j.**
-dontwarn org.jspecify.**

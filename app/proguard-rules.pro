# jmrtd / scuba / spongycastle use reflection and service loaders.
-keep class org.jmrtd.** { *; }
-keep class net.sf.scuba.** { *; }
-keep class org.spongycastle.** { *; }
-dontwarn org.jmrtd.**
-dontwarn net.sf.scuba.**
-dontwarn org.spongycastle.**
-dontwarn javax.naming.**

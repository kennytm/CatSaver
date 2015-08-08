###### Guava
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe

-keepattributes RuntimeVisibleAnnotations #,SourceFile,LineNumberTable

-keepclassmembers,allowobfuscation class * {
    @com.google.common.eventbus.Subscribe public void *(**);
}

-assumenosideeffects class com.google.common.base.Preconditions {
    *** check*(...);
}
# ^ no need to perform the conservative checks in release mode... (this saves ~3 KB)

###### Chunk

-dontwarn sun.misc.BASE64Decoder
-dontwarn sun.misc.BASE64Encoder
# ^ we don't use base64 filters

-assumenosideeffects class com.csvreader.CsvReader { *; }
# ^ we are not using the *.csv files for localization, it's safe to ignore it. (this saves ~8 KB)

-dontwarn org.cheffo.jeplite.**
# ^ we don't use the `calc` filter, only `qcalc` which doesn't need the expression parser

-dontwarn com.madrobot.beans.**
-dontwarn java.beans.**
# ^ we don't use Java Beans.

-assumenosideeffects class com.x5.template.MacroTag {
    *** *Json*(...);
}
# ^ we don't use macros. Don't let it introduce yet another JSON library. (this saves ~4 KB)

###### TOML

-keepclassmembers class hihex.cs.LogEntryFilter$Raw* {
    <fields>;
}

###### Optimization

-optimizations !class/merging/horizontal
# ^ Disabled until we figure out how to avoid the VerifyError due to those java.beans.**.
-optimizationpasses 1
-allowaccessmodification
-verbose

###### Note that the gradle plugin will automatically generate the following options, so we don't
###### need to specify these, even if we don't include proguard-android.txt:
######
###### 1. -printusage, -printseeds, -printmapping, -dump
###### 2. Every code reference from AndroidManifest.xml and the layout.xml

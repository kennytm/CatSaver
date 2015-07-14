###### Guava
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn sun.misc.Unsafe

-keepattributes RuntimeVisibleAnnotations
-keep @interface com.google.common.eventbus.Subscribe

-keepclassmembers,allowobfuscation class * {
    @com.google.common.eventbus.Subscribe public void *(**);
}

###### Chunk
-dontwarn sun.misc.BASE64Decoder
-dontwarn sun.misc.BASE64Encoder
# ^ we don't use base64 filters
-dontwarn org.cheffo.jeplite.**
# ^ we don't use the `calc` filter, only `qcalc` which doesn't need the expression parser
-dontwarn com.madrobot.beans.**
-dontwarn java.beans.**
# ^ we don't use Java Beans.
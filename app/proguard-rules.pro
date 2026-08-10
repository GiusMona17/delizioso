# Add project specific ProGuard rules here.
# Keep kotlinx.serialization generated serializers (R8 handles them via the plugin,
# but keep rules are harmless if minification is enabled later).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# R8 rules for the release build (shrink + obfuscate + optimise).
# Libraries that ship consumer rules (Room, WorkManager, Compose, Media3, Tink) are covered by those files.

# Remove every android.util.Log call in release so nothing about internal state is written to logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Flatten everything into one anonymous package and allow cross-class optimisation: smaller, harder to read after decompiling.
-repackageclasses ''
-allowaccessmodification
-optimizationpasses 5

# Strip file names and line numbers from stack traces (they only help someone reading a crash dump or the decompiled code).
-renamesourcefileattribute ''

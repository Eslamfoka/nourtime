# Release builds drop debug and info logs: they name the apps the child opens (Play privacy policy).
# Warnings and errors stay, for crash diagnosis.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

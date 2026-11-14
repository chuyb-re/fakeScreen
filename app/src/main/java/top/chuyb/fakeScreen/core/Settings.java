package top.chuyb.fakeScreen.core;

public class Settings {
    private static boolean CompatibilityModeOn=false;
    private static boolean DebugModeOn=false;

    public static boolean isCompatibilityModeOn() {
        return CompatibilityModeOn;
    }

    public static void setCompatibilityModeOn(boolean compatibilityModeOn) {
        CompatibilityModeOn = compatibilityModeOn;
    }

    public static boolean isDebugModeOn() {
        return DebugModeOn;
    }

    public static void setDebugModeOn(boolean debugModeOn) {
        DebugModeOn = debugModeOn;
    }
}

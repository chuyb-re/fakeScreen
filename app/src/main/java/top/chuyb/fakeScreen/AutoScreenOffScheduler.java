package top.chuyb.fakeScreen;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

/**
 * Module-side fake screen-off scheduler. It turns the display off directly and
 * never intentionally enters the normal sleep/lock path.
 */
final class AutoScreenOffScheduler {

    private static final String TAG = "FakeScreen";
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    private static Runnable sPendingOffTask;
    private static XposedModule sModule;
    private static ClassLoader sClassLoader;
    private static volatile int sSeconds = 0;
    private static volatile boolean sEnabled = false;

    // Guard task replacement and deadline checks against concurrent binder threads.
    private static long sGeneration = 0;
    private static long sLastUserActivity = SystemClock.elapsedRealtime();

    private AutoScreenOffScheduler() {
    }

    static synchronized void init(XposedModule module, ClassLoader classLoader) {
        sModule = module;
        sClassLoader = classLoader;
    }

    static synchronized void setEnabled(boolean enabled) {
        sEnabled = enabled;
        if (enabled) {
            reschedule();
        } else {
            cancel();
        }
    }

    static synchronized void setSeconds(int seconds) {
        sSeconds = Math.max(0, seconds);
        if (sEnabled) {
            reschedule();
        } else {
            cancel();
        }
    }

    static synchronized void onDisplayToggled(boolean screenNowOff) {
        if (screenNowOff) {
            cancel();
        } else {
            sLastUserActivity = SystemClock.elapsedRealtime();
            reschedule();
        }
    }

    /** Reset the deadline whenever PowerManagerService receives user activity. */
    static synchronized void onUserActivity() {
        sLastUserActivity = SystemClock.elapsedRealtime();
        if (sEnabled) {
            reschedule();
        }
    }

    static synchronized void reschedule() {
        cancel();
        if (!sEnabled || sSeconds <= 0 || sModule == null || sClassLoader == null) {
            return;
        }
        // The display is already fake-off; do not arm another off task.
        if (DisplayPowerControl.isExpectingOn()) {
            return;
        }

        final int seconds = sSeconds;
        final long token = sGeneration;
        final Runnable task = new Runnable() {
            @Override
            public void run() {
                synchronized (AutoScreenOffScheduler.class) {
                    if (token != sGeneration) {
                        return; // stale callback that raced with a user activity
                    }
                    sPendingOffTask = null;
                    if (!sEnabled || sSeconds <= 0 || DisplayPowerControl.isExpectingOn()) {
                        return;
                    }

                    long elapsed = SystemClock.elapsedRealtime() - sLastUserActivity;
                    long required = sSeconds * 1000L;
                    if (elapsed < required) {
                        // Defensive deadline check: never trust cancellation alone.
                        reschedule();
                        return;
                    }

                    try {
                        DisplayPowerControl.forceOff(sModule, sClassLoader);
                        sModule.log(Log.INFO, TAG, "Auto fake screen-off after " + seconds
                                + "s without user activity");
                    } catch (Throwable t) {
                        sModule.log(Log.ERROR, TAG, "Auto fake screen-off failed", t);
                    }
                }
            }
        };
        sPendingOffTask = task;
        sHandler.postDelayed(task, seconds * 1000L);
    }

    static synchronized void cancel() {
        // Increment even when no callback is queued, invalidating any callback
        // that has already started or is waiting to acquire this class monitor.
        sGeneration++;
        if (sPendingOffTask != null) {
            sHandler.removeCallbacks(sPendingOffTask);
            sPendingOffTask = null;
        }
    }
}

package rikka.shizuku.server;

import android.os.Handler;
import android.os.IBinder;
import android.os.Process;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;

import rikka.shizuku.server.util.Logger;

/**
 * Watches this process from the inside.
 *
 * The manager's watchdog only sees binder death. A server whose main thread is stuck keeps a
 * live binder and queues transactions behind a looper that never returns, so from the manager it
 * looks healthy while every app using it waits forever. This watchdog notices that silence, and
 * also notices when one of the system services the process is built on has gone away. In either
 * case the only recovery is a new process, so it logs why and kills this one; the manager's
 * existing watchdog then sees the binder die and starts a fresh server.
 */
public final class ServerWatchdog {

    private static final Logger LOGGER = new Logger("ServerWatchdog");

    /** How often the main thread says it is still there. */
    private static final long BEAT_INTERVAL_MS = 5_000;

    /** A minute of main-thread silence is a deadlock, not a slow operation. */
    private static final long HANG_TIMEOUT_MS = 60_000;

    /** How often both the hang and the system-service checks run. */
    private static final long CHECK_INTERVAL_MS = 5_000;

    /** Starting is legitimately busy; do not judge it during this window. */
    private static final long STARTUP_GRACE_MS = 30_000;

    /** The system services the server cannot work without. */
    private static final String[] REQUIRED_SERVICES = {"package", "activity", "user", "appops"};

    private static volatile long lastBeat;
    private static volatile boolean stopped;
    private static volatile Handler handler;
    private static Thread thread;

    private static final Runnable BEAT = new Runnable() {
        @Override
        public void run() {
            lastBeat = SystemClock.uptimeMillis();
            Handler current = handler;
            if (!stopped && current != null) {
                current.postDelayed(this, BEAT_INTERVAL_MS);
            }
        }
    };

    private ServerWatchdog() {
    }

    /**
     * Starts watching on {@code mainHandler}'s looper and on a thread of its own.
     *
     * Call once, after the system services the server waits for are available and the main
     * looper has been prepared.
     */
    public static synchronized void start(Handler mainHandler) {
        if (thread != null) return;

        handler = mainHandler;
        stopped = false;
        lastBeat = SystemClock.uptimeMillis();
        mainHandler.post(BEAT);

        final long startedAt = SystemClock.uptimeMillis();
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopped) {
                    try {
                        Thread.sleep(CHECK_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        return;
                    }

                    if (stopped || SystemClock.uptimeMillis() - startedAt < STARTUP_GRACE_MS) {
                        continue;
                    }

                    long silentFor = SystemClock.uptimeMillis() - lastBeat;
                    if (silentFor > HANG_TIMEOUT_MS) {
                        logHang(silentFor);
                        die("main thread has not answered for " + silentFor + " ms");
                        return;
                    }

                    String dead = deadSystemService();
                    if (dead != null) {
                        die("system service " + dead + " is gone");
                        return;
                    }
                }
            }
        }, "server-watchdog");
        thread.setDaemon(true);
        thread.start();
    }

    /** Stops watching for exits that are meant to happen. */
    public static synchronized void stop() {
        stopped = true;
        handler = null;
        Thread running = thread;
        thread = null;
        if (running != null) running.interrupt();
    }

    /**
     * The first required system service that no longer answers, or null when all do.
     *
     * The question is asked of ServiceManager every time rather than of a cached proxy: a cached
     * proxy whose binder died is exactly what cannot be trusted here.
     */
    private static String deadSystemService() {
        for (String name : REQUIRED_SERVICES) {
            try {
                IBinder binder = ServiceManager.getService(name);
                if (binder == null || !binder.pingBinder()) {
                    return name;
                }
            } catch (Throwable tr) {
                LOGGER.w(tr, "could not ask about the %s service", name);
                return name;
            }
        }
        return null;
    }

    private static void logHang(long silentFor) {
        Thread main = android.os.Looper.getMainLooper().getThread();
        StringBuilder trace = new StringBuilder();
        trace.append("The main thread has not answered for ").append(silentFor).append(" ms");
        for (StackTraceElement element : main.getStackTrace()) {
            trace.append("\n    at ").append(element);
        }
        Log.e("ServerWatchdog", trace.toString());
        LOGGER.e(trace.toString());
    }

    /** Ends this process, which is the manager's cue to start another one. */
    private static void die(String reason) {
        stopped = true;
        handler = null;
        LOGGER.e("exiting so the manager can start a fresh server: %s", reason);
        Process.killProcess(Process.myPid());
    }
}

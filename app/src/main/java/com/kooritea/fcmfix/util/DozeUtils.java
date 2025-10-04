package com.kooritea.fcmfix.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

public class DozeUtils extends BroadcastReceiver {
    public final static String TAG = "DozeUtils";
    private static final String NETLINK_UNIT_PATH = "/proc/rekernel";
    private static final int USER_PORT = 100;
    private static final int NETLINK_UNIT_DEFAULT = 22;
    private static final int NETLINK_UNIT_MAX = 26;
    private static int netlinkUnit = NETLINK_UNIT_DEFAULT;

    public static boolean isKernelAvailable() {
        try {
            return new java.io.File(NETLINK_UNIT_PATH).exists();
        } catch (Exception e) {
            Log.e(TAG, "[rekernel] check kernel error", e);
            return false;
        }
    }

    private static int getNetlinkUnit() {
        try {
            java.io.File[] files = new java.io.File(NETLINK_UNIT_PATH).listFiles();
            if (files != null && files.length > 0) {
                String unitStr = files[0].getName();
                try {
                    int unit = Integer.parseInt(unitStr);
                    if (unit >= NETLINK_UNIT_DEFAULT && unit <= NETLINK_UNIT_MAX) {
                        return unit;
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "[rekernel] parse unit error: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "[rekernel] read unit error: " + e.getMessage());
        }
        return netlinkUnit;
    }

    private static boolean checkAppRunning(Context context, String pkg) {
        if (context == null || pkg == null) return false;
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return false;
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs == null) return false;
            for (android.app.ActivityManager.RunningAppProcessInfo pi : procs) {
                if (pi == null) continue;
                String pname = pi.processName;
                if (pname == null) continue;
                if (pname.equals(pkg) || pname.startsWith(pkg + ":")) {
                    return true;
                }
            }
        } catch (Throwable e) {
            Log.e(TAG, "[rekernel] check app running error: " + pkg, e);
        }
        return false;
    }

    public static boolean isAppRunning(Context context, String pkg) {
        return checkAppRunning(context, pkg);
    }

    public static void activeApp(Context context, String pkg) {
        try {
            if (!isKernelAvailable()) {
                Log.e(TAG, "[rekernel] kernel not available");
                return;
            }

            netlinkUnit = getNetlinkUnit();
            
            LocalSocket socket = null;
            java.io.OutputStream out = null;
            java.io.InputStream in = null;
            try {
                socket = new LocalSocket();
                // use abstract namespace which is how kernel usually exposes netlink with name like "rekernel.%d"
                LocalSocketAddress addr = new LocalSocketAddress("rekernel." + netlinkUnit, LocalSocketAddress.Namespace.ABSTRACT);
                socket.connect(addr);
                try {
                    socket.setSoTimeout(1000); // avoid blocking read forever
                } catch (Exception ignored) {}

                // Use Re-Kernel's command protocol
                String request = String.format("type=unfreeze,package=%s;\n", pkg);
                out = socket.getOutputStream();
                out.write(request.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.flush();

                in = socket.getInputStream();
                byte[] buffer = new byte[256]; // standard
                int read = in.read(buffer);
                if (read > 0) {
                    String response = new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8);
                    Log.d(TAG, "[rekernel] response: " + response);
                }
                Log.i(TAG, "[rekernel] unfreeze request sent for " + pkg);
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) {}
                try { if (out != null) out.close(); } catch (Throwable ignored) {}
                try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable e) {
            Log.e(TAG, "[rekernel] activeApp error for " + pkg, e);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
    }
}
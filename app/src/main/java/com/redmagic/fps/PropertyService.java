package com.redmagic.fps;

import android.os.Process;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Runs in the Shizuku UserService process, never in the app's own identity. */
public final class PropertyService extends IPropertyService.Stub {
    private static final String KEY = "vendor.gpp.allgame.enable";
    private String[] run(String... command) {
        if (Process.myUid() != 2000) return new String[]{"-1", "", "FPS_UID_REQUIRED"};
        try {
            java.lang.Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new String[]{"-1", "", "FPS_TIMEOUT"};
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[1024]; int n;
                while ((n = in.read(buffer)) != -1) output.write(buffer, 0, n);
            }
            String text = output.toString("UTF-8").trim();
            return process.exitValue() == 0 ? new String[]{"0", text, ""} : new String[]{String.valueOf(process.exitValue()), "", text};
        } catch (Exception e) {
            return new String[]{"-1", "", e.toString()};
        }
    }
    @Override public String[] readProperty() { return run("/system/bin/getprop", KEY); }
    @Override public String[] writeProperty(int value) {
        if (value != 0 && value != 1) return new String[]{"-1", "", "FPS_INVALID_VALUE"};
        return run("/system/bin/setprop", KEY, String.valueOf(value));
    }
    @Override public void destroy() { System.exit(0); }
}

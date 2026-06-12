package com.bbttvv.build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class BumpReleaseVersionTask extends DefaultTask {
    @OutputFile
    public abstract RegularFileProperty getVersionFile();

    public BumpReleaseVersionTask() {
        getOutputs().upToDateWhen(task -> false);
    }

    @TaskAction
    public void bump() throws IOException {
        File file = getVersionFile().get().getAsFile();
        AppVersion current = readAppVersion(file);
        AppVersion next = new AppVersion(
                current.getVersionCode() + 1,
                incrementVersionName(current.getVersionName())
        );
        writeAppVersion(file, next);
        getLogger().lifecycle(
                "BBTTVV release version bumped to "
                        + next.getVersionName()
                        + " ("
                        + next.getVersionCode()
                        + ")"
        );
    }

    public static AppVersion readAppVersion(File file) {
        Properties properties = new Properties();
        if (file.exists()) {
            try (FileInputStream input = new FileInputStream(file)) {
                properties.load(input);
            } catch (IOException error) {
                throw new IllegalStateException("Failed to read app version file: " + file, error);
            }
        }
        return new AppVersion(
                parseVersionCode(properties.getProperty("versionCode")),
                readVersionName(properties.getProperty("versionName"))
        );
    }

    private static int parseVersionCode(String value) {
        if (value == null || value.isBlank()) {
            return 2;
        }
        return Integer.parseInt(value.trim());
    }

    private static String readVersionName(String value) {
        if (value == null || value.isBlank()) {
            return "1.00";
        }
        return value.trim();
    }

    private static void writeAppVersion(File file, AppVersion version) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        String payload = "versionCode="
                + version.getVersionCode()
                + "\nversionName="
                + version.getVersionName()
                + "\n";
        Files.writeString(file.toPath(), payload, StandardCharsets.UTF_8);
    }

    private static String incrementVersionName(String versionName) {
        String[] parts = versionName.trim().split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("versionName must use decimal format like 1.00");
        }
        int major = Integer.parseInt(parts[0]);
        int hundredths = Integer.parseInt(parts[1]);
        int nextHundredths = hundredths + 1;
        if (nextHundredths >= 100) {
            return (major + 1) + ".00";
        }
        return major + "." + String.format(Locale.ROOT, "%02d", nextHundredths);
    }

    public static final class AppVersion {
        private final int versionCode;
        private final String versionName;

        public AppVersion(int versionCode, String versionName) {
            this.versionCode = versionCode;
            this.versionName = versionName;
        }

        public int getVersionCode() {
            return versionCode;
        }

        public String getVersionName() {
            return versionName;
        }
    }
}

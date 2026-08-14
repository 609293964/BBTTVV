package com.bbttvv.build;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

public abstract class ExportReleaseApksTask extends DefaultTask {
    private static final List<String> RELEASE_ABIS = Arrays.asList("arm64-v8a", "armeabi-v7a");

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getPackagedApkDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getVersionFile();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void export() throws IOException {
        File packagedDirectory = getPackagedApkDirectory().get().getAsFile();
        File destinationDirectory = getOutputDirectory().get().getAsFile();
        BumpReleaseVersionTask.AppVersion version =
                BumpReleaseVersionTask.readAppVersion(getVersionFile().get().getAsFile());

        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            throw new IOException("Failed to create release output directory: " + destinationDirectory);
        }
        File[] staleApks = destinationDirectory.listFiles(
                file -> file.isFile() && file.getName().toLowerCase().endsWith(".apk")
        );
        if (staleApks != null) {
            for (File staleApk : staleApks) {
                Files.delete(staleApk.toPath());
            }
        }

        File[] packagedApks = packagedDirectory.listFiles(
                file -> file.isFile() && file.getName().toLowerCase().endsWith(".apk")
        );
        if (packagedApks == null) {
            throw new IllegalStateException("No packaged release APKs found in " + packagedDirectory);
        }

        for (String abi : RELEASE_ABIS) {
            File source = Arrays.stream(packagedApks)
                    .filter(file -> file.getName().contains(abi))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing " + abi + " release APK in " + packagedDirectory
                    ));
            File destination = new File(
                    destinationDirectory,
                    "BBTTVV-" + version.getVersionName() + "-" + abi + ".apk"
            );
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getLogger().lifecycle("Exported " + destination.getAbsolutePath());
        }
    }
}

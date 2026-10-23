package etalii.adp.atd;

import java.nio.file.Files;
import java.nio.file.Path;

import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.system.CpuArch;

/**
 * The {@code atd-lsp} the plug-in ships for this platform (etalii.adp spec 035, T117): the build fetches the desktop
 * host's release that {@code atd-lsp.lock} names, checks each file's SHA-256 and packs the server of each platform
 * into the plug-in's {@code bin/<platform>/}.
 */
final class AtdServerBinary {

    private AtdServerBinary() {
    }

    /** The platform's folder under {@code bin/}, or none where no server is released for it. */
    static @Nullable String platform() {
        if (SystemInfo.isWindows && CpuArch.isIntel64()) {
            return "windows-x64";
        }
        if (SystemInfo.isMac && CpuArch.isArm64()) {
            return "macos-arm64";
        }
        if (SystemInfo.isLinux && CpuArch.isIntel64()) {
            return "linux-x64";
        }
        return null;
    }

    /** The server's executable, or none where it is not packed for this platform. */
    static @Nullable Path path() {
        // This class's jar is in the plug-in's lib/, beside its bin/.
        String jar = PathManager.getJarPathForClass(AtdServerBinary.class);
        String platform = platform();
        if (jar == null || platform == null || Path.of(jar).getParent() == null || Path.of(jar).getParent().getParent() == null) {
            return null;
        }
        Path binary = Path.of(jar).getParent().getParent().resolve("bin").resolve(platform).resolve(SystemInfo.isWindows ? "atd-lsp.exe" : "atd-lsp");
        if (!Files.isRegularFile(binary)) {
            return null;
        }
        // A plug-in zip unpacked by the IDE may lose the executable bit.
        if (!SystemInfo.isWindows && !Files.isExecutable(binary)) {
            binary.toFile().setExecutable(true);
        }
        return binary;
    }
}

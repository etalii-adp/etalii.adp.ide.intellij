package etalii.adp.freemind;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Gives a {@code .mm} file the {@link FreeMindFileType} when {@link FreeMindSniffer} recognises its
 * start. The IDE only asks for files no file type claims by extension, so where {@code .mm} belongs
 * to another type the editor provider's own sniff alone decides (research R3).
 */
public final class FreeMindFileTypeDetector implements FileTypeRegistry.FileTypeDetector {

    @Override
    public @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        String extension = file.getExtension();
        if (extension == null || !"mm".equals(extension.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return FreeMindSniffer.isFreeMind(firstBytes.toBytes()) ? FreeMindFileType.INSTANCE : null;
    }

    @Override
    public int getDesiredContentPrefixLength() {
        return FreeMindSniffer.LIMIT;
    }
}

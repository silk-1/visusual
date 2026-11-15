package silkvis.media;

import java.io.IOException;
import java.nio.file.Path;

/** Native helper executable: push-based, verified by the shipped manifest. */
public final class ExeHelperLauncher implements MediaHelperLauncher {
    private final MediaHelperBinary binary;

    public ExeHelperLauncher(MediaHelperBinary binary) {
        this.binary = binary;
    }

    @Override
    public String providerId() {
        return "windows-gsmtc";
    }

    @Override
    public String verify() {
        return binary.verify();
    }

    @Override
    public ProcessBuilder newProcessBuilder() {
        ProcessBuilder builder = new ProcessBuilder(binary.binaryPath().toString(), "--protocol", "1");
        Path dir = binary.binaryPath().getParent();
        if (dir != null) {
            builder.directory(dir.toFile());
        }
        return builder;
    }
}

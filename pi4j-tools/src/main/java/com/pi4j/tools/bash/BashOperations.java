package com.pi4j.tools.bash;

import com.pi4j.ai.provider.AbortHandle;
import java.io.IOException;
import java.nio.file.Path;

public interface BashOperations {
    BashResult exec(String command, Path cwd, int timeoutSeconds, AbortHandle abortHandle)
            throws IOException, InterruptedException;
}

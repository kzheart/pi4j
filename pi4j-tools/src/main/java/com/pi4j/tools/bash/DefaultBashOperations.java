package com.pi4j.tools.bash;

import com.pi4j.ai.provider.AbortHandle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class DefaultBashOperations implements BashOperations {

    @Override
    public BashResult exec(String command, Path cwd, int timeoutSeconds, AbortHandle abortHandle)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder("bash", "-lc", command)
                .directory(cwd.toFile())
                .start();

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        Thread stdoutThread = new Thread(() -> copy(process.getInputStream(), stdout), "bash-stdout");
        Thread stderrThread = new Thread(() -> copy(process.getErrorStream(), stderr), "bash-stderr");
        stdoutThread.setDaemon(true);
        stderrThread.setDaemon(true);
        stdoutThread.start();
        stderrThread.start();

        Runnable abortListener = () -> process.destroyForcibly();
        if (abortHandle != null) {
            abortHandle.addListener(abortListener);
        }

        boolean finished;
        try {
            finished = process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS);
        } finally {
            if (abortHandle != null) {
                abortHandle.removeListener(abortListener);
            }
        }

        boolean timedOut = !finished;
        if (timedOut) {
            process.destroyForcibly();
        }

        stdoutThread.join(1000);
        stderrThread.join(1000);

        int exitCode = timedOut ? -1 : process.exitValue();
        return new BashResult(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8.name()),
                stderr.toString(StandardCharsets.UTF_8.name()),
                timedOut);
    }

    private void copy(InputStream inputStream, ByteArrayOutputStream outputStream) {
        byte[] buffer = new byte[4096];
        int read;
        try {
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException ignored) {
        }
    }
}

package dk.mada.backup.restore;

import dk.mada.backup.cli.Console;
import dk.mada.backup.impl.ExitHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes a restore script.
 */
public final class RestoreExecutor {
    private RestoreExecutor() {
        // empty
    }

    /**
     * Runs restore script.
     *
     * @param script       the restore script
     * @param envOverrides provided environment overrides
     * @param args         restore script arguments
     * @return the result of executing the script
     */
    public static Result runRestoreScript(Path script, Map<String, String> envOverrides, String... args) {
        return runCmd(script, envOverrides, args);
    }

    /**
     * Runs restore script.
     *
     * @param exitHandler  the exit handler to use
     * @param script       the restore script
     * @param envOverrides provided environment overrides
     * @param args         restore script arguments
     * @return the result of executing the script
     */
    public static String runRestoreScriptExitOnFail(
            ExitHandler exitHandler, Path script, Map<String, String> envOverrides, String... args) {
        Result res = runCmd(script, envOverrides, args);
        if (res.exitValue != 0) {
            Console.println("Failed to run " + script + ", returned " + res.exitValue);
            exitHandler.systemExitMessage(res.exitValue, res.output);
        }

        return res.output;
    }

    private static Result runCmd(Path script, Map<String, String> envOverrides, String... args) {
        Path runInDir = script.getParent();
        if (runInDir == null) {
            runInDir = Paths.get(".");
        }
        List<String> cmd =
                new ArrayList<>(List.of("/bin/bash", script.toAbsolutePath().toString()));
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(runInDir.toFile()).redirectErrorStream(true);

        pb.environment().putAll(envOverrides);

        try {
            Process p = pb.start();
            String output = readOutput(p);

            return new Result(p.waitFor(), output);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run restore script " + script, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted restore script " + script, e);
        }
    }

    private static String readOutput(Process p) throws IOException {
        try (InputStream in = p.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Result from running external process.
     *
     * @param exitValue the process exit value
     * @param output    the (combined) stdout and stderr output
     */
    public record Result(int exitValue, String output) {}
}

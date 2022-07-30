package dk.mada.backup.restore;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dk.mada.backup.cli.Console;

/**
 * Executes a restore script.
 */
public final class RestoreExecutor {
    private RestoreExecutor() { }

    /**
     * Runs restore script.
     *
     * @param script the restore script
     * @param envOverrides provided environment overrides
     * @param args restore script arguments
     * @return the result of executing the script
     */
    public static Result runRestoreScript(Path script, Map<String, String> envOverrides, String... args) {
        return runCmd(script, envOverrides, args);
    }

    /**
     * Runs restore script.
     *
     * @param avoidSystemExit flag to disable use of System.exit. Used from tests
     * @param script the restore script
     * @param envOverrides provided environment overrides
     * @param args restore script arguments
     * @return the result of executing the script
     */
    public static String runRestoreScriptExitOnFail(boolean avoidSystemExit, Path script,
            Map<String, String> envOverrides, String... args) {
        Result res = runCmd(script, envOverrides, args);
        if (res.exitValue != 0) {
            Console.println("Failed to run " + script + ", returned " + res.exitValue);
            Console.println(res.output);

            if (avoidSystemExit) {
                throw new IllegalStateException("Restore operation failed, exit " + res.exitValue
                        + ", output: " + res.output);
            } else {
                System.exit(1);
            }
        }

        return res.output;
    }

    private static Result runCmd(Path script, Map<String, String> envOverrides, String... args) {
        List<String> cmd = new ArrayList<>(List.of("/bin/bash", script.toAbsolutePath().toString()));
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd)
                .directory(script.getParent().toFile())
                .redirectErrorStream(true);

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
            return new String(in.readAllBytes());
        }
    }

    public static class Result {
        public final int exitValue;
        public final String output;

        public Result(int exitValue, String output) {
            super();
            this.exitValue = exitValue;
            this.output = output;
        }
    }
}

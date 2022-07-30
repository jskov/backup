package dk.mada.backup.restore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import dk.mada.backup.BackupElement;
import dk.mada.backup.api.BackupTargetExistsException;

/**
 * Copies out the restore script, replacing backup information as it goes.
 */
public class RestoreScriptWriter {
    public void write(Path script, Map<VariableName, String> vars, List<? extends BackupElement> crypts,
            List<? extends BackupElement> tars, List<? extends BackupElement> files) {
        if (Files.exists(script)) {
            throw new BackupTargetExistsException("Restore script file " + script + " already exists!");
        }

        try (InputStream is = getClass().getResourceAsStream("/restore.sh");
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                BufferedWriter bw = Files.newBufferedWriter(script, StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE)) {
            String line;
            boolean ignoringSection = false;
            while ((line = br.readLine()) != null) {
                String addLine = line;
                if (line.startsWith("#BEGIN_")) {
                    ignoringSection = true;
                } else if (line.startsWith("#END_CRYPTS")) {
                    addLine = elementsToText(crypts);
                    ignoringSection = false;
                } else if (line.startsWith("#END_ARCHIVES")) {
                    addLine = elementsToText(tars);
                    ignoringSection = false;
                } else if (line.startsWith("#END_FILES")) {
                    addLine = elementsToText(files);
                    ignoringSection = false;
                }

                if (!ignoringSection) {
                    String expanded = expandVars(vars, addLine);

                    bw.write(expanded);
                    bw.append('\n');
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create restore script " + script, e);
        }

        makeScriptExecutable(script);
    }

    private String expandVars(Map<VariableName, String> vars, String line) {
        String res = line;

        for (Map.Entry<VariableName, String> e : vars.entrySet()) {
            res = res.replace("@@" + e.getKey().name() + "@@", e.getValue());
        }

        return res;
    }

    /**
     * Makes script executable by owner/group.
     *
     * Used to include others, but cannot think of a good reason to do this.
     *
     * @param script the script file to make executable
     */
    private void makeScriptExecutable(Path script) {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(script);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(script, perms);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to make script executable " + script, e);
        }
    }

    private String elementsToText(List<? extends BackupElement> elements) {
        return elements.stream()
                .map(BackupElement::toBackupSummary)
                .collect(Collectors.joining("\n"));
    }
}

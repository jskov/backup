package dk.mada.backup.restore;

import dk.mada.backup.BackupElement;
import dk.mada.backup.api.BackupTargetExistsException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Copies out the restore script, replacing backup information as it goes.
 */
public final class RestoreScriptWriter {
    /** The script format version generated by this instance. */
    private static final DataFormatVersion FORMAT_VERSION = DataFormatVersion.VERSION_2;
    /** Variable indexers for crypt lines */
    private static final Map<VariableName, String> SCRIPT_INDEXERS = Map.of(
            VariableName.VARS_MD5,
                    """
                    local size=${l:0:11}
                    local xxh3=${l:12:16}
                    local md5=${l:29:32}
                    local file=${l:62}""",
            VariableName.VARS,
                    """
                    local size=${l:0:11}
                    local xxh3=${l:12:16}
                    local file=${l:29}""");
    /** The variables values to expand in the script. */
    private final Map<VariableName, String> vars;
    /** The encrypted file data to add to the script. */
    private final List<? extends BackupElement> crypts;
    /** The tar file data to add to the script. */
    private final List<? extends BackupElement> tars;
    /** The file data to add to the script. */
    private final List<? extends BackupElement> files;

    /**
     * Creates new instance.
     *
     * @param vars   the variables to expand in the script template
     * @param crypts the information about crypted files
     * @param tars   the information about tar files
     * @param files  the information about the origin files
     */
    public RestoreScriptWriter(
            Map<VariableName, String> vars,
            List<? extends BackupElement> crypts,
            List<? extends BackupElement> tars,
            List<? extends BackupElement> files) {
        this.vars = vars;
        this.crypts = crypts;
        this.tars = tars;
        this.files = files;
    }

    /**
     * Constructs and writes restore script.
     *
     * Note that the lists are added to the script in the order provided.
     *
     * @param script the destination path for the script
     */
    public void write(Path script) {
        if (Files.exists(script)) {
            throw new BackupTargetExistsException("Restore script file " + script + " already exists!");
        }

        try (InputStream is = getClass().getResourceAsStream("/restore.sh");
                InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr);
                BufferedWriter bw =
                        Files.newBufferedWriter(script, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
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
                    String expanded = expandVars(addLine);

                    bw.write(expanded);
                    bw.append('\n');
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create restore script " + script, e);
        }

        makeScriptExecutable(script);
    }

    private String expandVars(String line) {
        String res = line;

        res = replaceVariable(res, VariableName.DATA_FORMAT_VERSION, FORMAT_VERSION.toString());

        for (Map.Entry<VariableName, String> e : vars.entrySet()) {
            res = replaceVariable(res, e.getKey(), e.getValue());
        }

        for (Map.Entry<VariableName, String> e : SCRIPT_INDEXERS.entrySet()) {
            res = replaceVariable(res, e.getKey(), e.getValue());
        }

        return res;
    }

    private String replaceVariable(String l, VariableName name, String value) {
        String m = "@@" + name + "@@";
        int index = l.indexOf(m);
        if (index < 0) {
            return l;
        }
        String indent = " ".repeat(index);
        String indentedValue = value.replace("\n", "\n" + indent);
        return l.replace(m, indentedValue);
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
        return elements.stream().map(BackupElement::toBackupSummary).collect(Collectors.joining("\n"));
    }
}

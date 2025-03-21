package dk.mada.backup.cli;

import dk.mada.backup.types.GpgId;
import picocli.CommandLine.ITypeConverter;

/**
 * Validates the GPG identity argument.
 */
public final class GpgRecipientConverter implements ITypeConverter<GpgId> {
    /** Creates new instance. */
    public GpgRecipientConverter() {
        // silence sonarcloud
    }

    @Override
    public GpgId convert(String value) throws Exception {
        return new GpgId(value);
    }
}

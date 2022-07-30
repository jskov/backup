package dk.mada.backup.cli;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

/**
 * Validates the GPG id argument.
 */
public final class GpgRecipientValidator implements IParameterValidator {
    /** Size of a GPG ID string. */
    private static final int GPG_ID_LENGTH = 40;

    @Override
    public void validate(String name, String value) throws ParameterException {
        if (value == null || value.length() != GPG_ID_LENGTH) {
            throw new ParameterException("Recipient id must be a 40 chars string");
        }
    }
}

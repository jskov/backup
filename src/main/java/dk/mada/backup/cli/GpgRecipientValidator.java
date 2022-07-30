package dk.mada.backup.cli;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;

/**
 * Validates the GPG id argument.
 */
public class GpgRecipientValidator implements IParameterValidator {

    @Override
    public void validate(String name, String value) throws ParameterException {
        if (value == null || value.length() != 40) {
            throw new ParameterException("Recipient id must be a 40 chars string");
        }
    }
}

package dk.mada.unit.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dk.mada.backup.cli.HumanSizeInputConverter;

/**
 * Test conversion of human size input converter.
 */
class HumanSizeInputConverterTest {
    @Test
    void shouldAcceptPlain() {
        HumanSizeInputConverter sut = new HumanSizeInputConverter();
        assertThatThrownBy(() -> sut.convert("4o2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "42,         42",
            "2K,         2048",
            "16k,        16384",
            "1M,         1048576",
            "1G,         1073741824",
            "1_000_000,  1000000"
    })
    void testWithCsvSource(String humanInput, long value) {
        long out = new HumanSizeInputConverter().convert(humanInput);

        assertThat(out)
                .isEqualTo(value);
    }
}

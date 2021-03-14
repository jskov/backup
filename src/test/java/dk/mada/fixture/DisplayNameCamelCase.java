package dk.mada.fixture;


import java.lang.reflect.Method;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayNameGenerator;

/**
 * Makes prettier test output names from camelcase input.
 */
public class DisplayNameCamelCase extends DisplayNameGenerator.Standard {
	private static final Pattern UPPERCASE = Pattern.compile("([A-Z])");
	private static final Pattern FIRST = Pattern.compile("^.");
	
	public DisplayNameCamelCase() {
    }

	@Override
    public String generateDisplayNameForClass(Class<?> testClass) {
        return this.replaceCapitals(super.generateDisplayNameForClass(testClass));
    }

	@Override
    public String generateDisplayNameForNestedClass(Class<?> nestedClass) {
        return this.replaceCapitals(super.generateDisplayNameForNestedClass(nestedClass));
    }

	@Override
    public String generateDisplayNameForMethod(Class<?> testClass, Method testMethod) {
        return this.replaceCapitals(testMethod.getName());
    }

    private String replaceCapitals(String name) {
		name = UPPERCASE.matcher(name).replaceAll(m -> " " + m.group().toLowerCase()).trim();
		name = FIRST.matcher(name).replaceAll(m -> " " + m.group().toUpperCase());
        name = name.replaceAll("([0-9].)", " $1");
        return name;
    }
}
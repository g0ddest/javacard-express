package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecutionException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Version of the converted package (JCVM 3.1 &sect;4.5). Both numbers are u1 items of the
 * package_info of the Header component (&sect;6.4), so each is 0 to 255; the recommended initial
 * version is 1.0.
 *
 * @param major major version, incremented for binary-incompatible changes
 * @param minor minor version, incremented for binary-compatible changes
 */
record PackageVersion(int major, int minor) {

    /** The documented default, 1.0. */
    static final PackageVersion DEFAULT = new PackageVersion(1, 0);

    private static final int MAX = 255;
    private static final Pattern FORMAT = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})");

    /**
     * Parses {@code <major>.<minor>}.
     *
     * @param text the configured value; {@code null} or blank means the default 1.0
     * @return the version
     * @throws MojoExecutionException if the value is not two numbers from 0 to 255 separated by a dot
     */
    static PackageVersion parse(String text) throws MojoExecutionException {
        if (text == null || text.isBlank()) {
            return DEFAULT;
        }
        Matcher m = FORMAT.matcher(text.trim());
        if (m.matches()) {
            int major = Integer.parseInt(m.group(1));
            int minor = Integer.parseInt(m.group(2));
            if (major <= MAX && minor <= MAX) {
                return new PackageVersion(major, minor);
            }
        }
        throw new MojoExecutionException("Invalid <packageVersion> '" + text + "': expected <major>.<minor> with"
                + " each number from 0 to 255 (u1 items of the Header component, JCVM 3.1 §4.5, §6.4), e.g. 1.0.");
    }

    @Override
    public String toString() {
        return major + "." + minor;
    }
}

package name.velikodniy.jcexpress.livecard.sim;

import name.velikodniy.jcexpress.livecard.live.TestApplet;
import name.velikodniy.jcexpress.livecard.thirdparty.ThirdPartyApplet;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The class path of the applets the live suite runs: the test applets and the third-party applets built here. */
public final class TestClassPath {

    private TestClassPath() {
    }

    /**
     * Returns the class path entries of the test applets and the compiled third-party applets.
     *
     * @return the directories
     */
    public static List<Path> applets() {
        List<Path> directories = new ArrayList<>(List.of(TestApplet.classesDirectory()));
        directories.addAll(ThirdPartyApplet.builtClasses());
        return directories;
    }
}

package name.velikodniy.jcexpress.plugin.testing;

import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link MavenProjectHelper} that records attached artifacts instead of registering them.
 */
public final class RecordingProjectHelper implements MavenProjectHelper {

    /**
     * An attached artifact.
     *
     * @param type       artifact type (e.g. {@code cap})
     * @param classifier classifier, or {@code null}
     * @param file       the file
     */
    public record Attachment(String type, String classifier, File file) {
    }

    private final List<Attachment> attachments = new ArrayList<>();

    /** @return the recorded attachments */
    public List<Attachment> attachments() {
        return List.copyOf(attachments);
    }

    @Override
    public void attachArtifact(MavenProject project, File artifactFile, String artifactClassifier) {
        attachments.add(new Attachment("jar", artifactClassifier, artifactFile));
    }

    @Override
    public void attachArtifact(MavenProject project, String artifactType, File artifactFile) {
        attachments.add(new Attachment(artifactType, null, artifactFile));
    }

    @Override
    public void attachArtifact(MavenProject project, String artifactType, String artifactClassifier,
                               File artifactFile) {
        attachments.add(new Attachment(artifactType, artifactClassifier, artifactFile));
    }

    @Override
    public void addResource(MavenProject project, String resourceDirectory, List<String> includes,
                            List<String> excludes) {
        // not used by the plugin
    }

    @Override
    public void addTestResource(MavenProject project, String resourceDirectory, List<String> includes,
                                List<String> excludes) {
        // not used by the plugin
    }
}

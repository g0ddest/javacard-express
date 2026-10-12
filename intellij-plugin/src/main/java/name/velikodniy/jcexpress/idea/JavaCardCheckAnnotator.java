package name.velikodniy.jcexpress.idea;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marks the errors of the project's javac check in the editor, as javac reports them in the build: for the package
 * of the open file, after each edit, in the background. {@link CheckRequests} decides whether the build checks the
 * package and collects the current texts and settings; {@link CheckRun} runs the check; the result is cached per
 * package.
 */
public final class JavaCardCheckAnnotator
        extends ExternalAnnotator<JavaCardCheckAnnotator.Collected, CheckReport> {

    private static final Logger LOG = Logger.getInstance(JavaCardCheckAnnotator.class);

    /**
     * What {@link #collectInformation} hands to {@link #doAnnotate}.
     *
     * @param project the file's project
     * @param request the package and settings
     */
    public record Collected(Project project, CheckRequest request) {
    }

    @Override
    public @Nullable Collected collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {
        // with Java errors javac stops before the class files the check needs; the IDE shows those errors itself
        return hasErrors ? null : collectInformation(file);
    }

    @Override
    public @Nullable Collected collectInformation(@NotNull PsiFile file) {
        if (!(file instanceof PsiJavaFile javaFile)) {
            return null;
        }
        try {
            CheckRequest request = CheckRequests.of(javaFile);
            return request == null ? null : new Collected(file.getProject(), request);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            LOG.debug("JavaCard Express: cannot collect the check's settings", e);
            return null;
        }
    }

    @Override
    public @Nullable CheckReport doAnnotate(Collected collected) {
        return collected == null ? null : CheckCache.getInstance(collected.project()).reportFor(collected.request());
    }

    @Override
    public void apply(@NotNull PsiFile file, CheckReport report, @NotNull AnnotationHolder holder) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (report == null || virtualFile == null) {
            return;
        }
        CharSequence text = file.getViewProvider().getContents();
        for (CheckProblem problem : report.problemsIn(virtualFile.getPath())) {
            int[] range = problem.range(text);
            holder.newAnnotation(HighlightSeverity.ERROR, problem.message())
                    .range(new TextRange(range[0], range[1]))
                    .tooltip("<html>" + StringUtil.escapeXmlEntities(problem.message())
                            + "<br><small>JavaCard Express: the javac check of the build</small></html>")
                    .create();
        }
    }
}

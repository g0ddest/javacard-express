package name.velikodniy.jcexpress.idea;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The last result per package of a project, so that a package whose texts and settings did not change is not
 * compiled again, and the place that keeps the IDE log to one entry per distinct problem of the check itself.
 */
@Service(Service.Level.PROJECT)
public final class CheckCache {

    private static final Logger LOG = Logger.getInstance(CheckCache.class);

    private final Map<String, Entry> last = new ConcurrentHashMap<>();
    private final Set<String> logged = ConcurrentHashMap.newKeySet();

    /**
     * Returns the cache of a project.
     *
     * @param project the project
     * @return its cache
     */
    public static CheckCache getInstance(Project project) {
        return project.getService(CheckCache.class);
    }

    /**
     * Returns the report for a request: the cached one when nothing changed, else a new run of the check.
     *
     * @param request the package and settings
     * @return the report
     */
    CheckReport reportFor(CheckRequest request) {
        String slot = request.module() + "/" + request.packageName();
        String key = request.key();
        Entry entry = last.get(slot);
        if (entry != null && entry.key().equals(key)) {
            return entry.report();
        }
        CheckReport report;
        try {
            report = CheckRun.run(request, ProgressManager::checkCanceled);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (RuntimeException | LinkageError e) {
            report = CheckReport.notRun("the check failed: " + e);
        }
        last.put(slot, new Entry(key, report));
        report.note().ifPresent(this::logOnce);
        return report;
    }

    private void logOnce(String note) {
        if (logged.add(note)) {
            LOG.info("JavaCard Express: " + note);
        }
    }

    private record Entry(String key, CheckReport report) {
    }
}

package name.velikodniy.jcexpress.idea;

import java.util.List;
import java.util.Optional;

/**
 * The result of one run of the check.
 *
 * @param problems the errors the check reported
 * @param note     why the check did not run or did not finish, for the IDE log; empty when it ran
 */
record CheckReport(List<CheckProblem> problems, Optional<String> note) {

    CheckReport {
        problems = List.copyOf(problems);
    }

    /**
     * A run that found these errors.
     *
     * @param problems the errors
     * @return the report
     */
    static CheckReport of(List<CheckProblem> problems) {
        return new CheckReport(problems, Optional.empty());
    }

    /**
     * A run that could not check.
     *
     * @param reason why
     * @return the report, without errors
     */
    static CheckReport notRun(String reason) {
        return new CheckReport(List.of(), Optional.of(reason));
    }

    /**
     * Returns the errors in one file.
     *
     * @param path the file
     * @return its errors
     */
    List<CheckProblem> problemsIn(String path) {
        return problems.stream().filter(problem -> problem.path().equals(path)).toList();
    }
}

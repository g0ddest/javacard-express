package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecution;

/**
 * How Maven started the goal: an {@code <execution>} of the POM run by the lifecycle, or a goal
 * named on the command line ({@code mvn javacard-express:build}), and which version of the
 * plugin it resolved.
 *
 * @param executionId   the execution id ({@code default} for an execution without id,
 *                      {@code default-cli} on the command line)
 * @param commandLine   {@code true} if the goal was named on the command line
 * @param pluginVersion the plugin version Maven runs, or {@code null} if unknown
 */
record Invocation(String executionId, boolean commandLine, String pluginVersion) {

    /**
     * @param execution the execution Maven injects ({@code ${mojoExecution}}), may be {@code null}
     *                  outside Maven
     * @return how the goal was started
     */
    static Invocation of(MojoExecution execution) {
        if (execution == null) {
            return new Invocation("default", false, null);
        }
        boolean cli = execution.getSource() == MojoExecution.Source.CLI;
        String version = execution.getMojoDescriptor() == null ? null : execution.getVersion();
        return new Invocation(execution.getExecutionId(), cli, version);
    }
}

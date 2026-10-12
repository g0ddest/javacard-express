package name.velikodniy.jcexpress.idea;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.LanguageLevelUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.compiler.AnnotationProcessingConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Collects a {@link CheckRequest} for the package of a Java file, in a read action, when the build checks that
 * package: the file is main code of a module whose main compilation has the check on its annotation processor path,
 * the check is not turned off, and the package is one the check chooses (the configured packages; otherwise the
 * module's only package, or a package with an applet).
 */
final class CheckRequests {

    private static final String APPLET = "javacard.framework.Applet";

    private CheckRequests() {
    }

    /**
     * Returns the request for the package of a file.
     *
     * @param file the file in the editor
     * @return the request, or {@code null} when the build does not check that package
     */
    static @Nullable CheckRequest of(PsiJavaFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        String packageName = file.getPackageName();
        Project project = file.getProject();
        ProjectFileIndex index = ProjectFileIndex.getInstance(project);
        Module module = virtualFile == null ? null : index.getModuleForFile(virtualFile);
        if (module == null || packageName.isEmpty() || !index.isInSourceContent(virtualFile)
                || index.isInTestSourceContent(virtualFile)) {
            return null;
        }
        AnnotationProcessingConfiguration profile = CompilerConfiguration.getInstance(project)
                .getAnnotationProcessingConfiguration(module);
        List<String> processorPath = processorPath(profile);
        if (processorPath.isEmpty()) {
            return null;
        }
        Map<String, String> options = options(project, module, profile);
        if ("false".equalsIgnoreCase(options.get(ProcessorOptions.CHECK))
                || !checkedByTheBuild(project, module, packageName, options)) {
            return null;
        }
        return new CheckRequest(module.getName(), packageName, sources(project, module, packageName, index),
                classpath(module), processorPath, options, release(project, module));
    }

    /** The processor path of the main compilation, when it has the check; empty otherwise. */
    static List<String> processorPath(AnnotationProcessingConfiguration profile) {
        if (!profile.isEnabled() || profile.isObtainProcessorsFromClasspath()) {
            return List.of();
        }
        List<String> path = Arrays.stream(profile.getProcessorPath().split(File.pathSeparator))
                .filter(entry -> !entry.isBlank()).toList();
        boolean hasCheck = path.stream()
                .anyMatch(entry -> new File(entry).getName().startsWith(ProcessorOptions.CHECK_JAR));
        return hasCheck ? path : List.of();
    }

    /**
     * The processor options of the main compilation: the IDE's profile and javac options, then what the build
     * passes (the Maven integration), which wins. Values go to the check as they are: it treats an unresolved
     * expression as the build's check does (an export file entry that is not there is left to the conversion).
     */
    private static Map<String, String> options(Project project, Module module, AnnotationProcessingConfiguration profile) {
        Map<String, String> options = new TreeMap<>(profile.getProcessorOptions());
        ProcessorOptions.addFrom(CompilerConfiguration.getInstance(project).getAdditionalOptions(module), options);
        for (CheckOptionsSource source : CheckOptionsSource.EP.getExtensionList()) {
            source.options(module).ifPresent(options::putAll);
        }
        return options;
    }

    private static boolean checkedByTheBuild(Project project, Module module, String packageName,
                                             Map<String, String> options) {
        String names = options.get(ProcessorOptions.PACKAGE_NAME);
        if (ProcessorOptions.isSet(names)) {
            return Arrays.stream(names.split(",")).map(String::strip).anyMatch(packageName::equals);
        }
        return packagesOfTheMainCode(module, 2).size() <= 1 || hasApplet(project, module, packageName);
    }

    /** The packages with Java sources in the module's main source roots, at most {@code limit} of them. */
    private static Set<String> packagesOfTheMainCode(Module module, int limit) {
        Set<String> packages = new HashSet<>();
        PackageIndex index = PackageIndex.getInstance(module.getProject());
        for (VirtualFile root : ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.SOURCE)) {
            VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {
                @Override
                public boolean visitFile(VirtualFile file) {
                    if (!file.isDirectory() && "java".equals(file.getExtension()) && file.getParent() != null) {
                        String name = index.getPackageNameByDirectory(file.getParent());
                        if (name != null) {
                            packages.add(name);
                        }
                    }
                    return packages.size() < limit;
                }
            });
        }
        return packages;
    }

    private static boolean hasApplet(Project project, Module module, String packageName) {
        JavaPsiFacade psi = JavaPsiFacade.getInstance(project);
        PsiPackage psiPackage = psi.findPackage(packageName);
        PsiClass applet = psi.findClass(APPLET, module.getModuleWithLibrariesScope());
        if (psiPackage == null || applet == null) {
            return false;
        }
        return Arrays.stream(psiPackage.getClasses(module.getModuleScope(false)))
                .anyMatch(type -> type.isInheritor(applet, true));
    }

    private static List<CheckRequest.Source> sources(Project project, Module module, String packageName,
                                                     ProjectFileIndex index) {
        List<CheckRequest.Source> sources = new ArrayList<>();
        PsiPackage psiPackage = JavaPsiFacade.getInstance(project).findPackage(packageName);
        if (psiPackage == null) {
            return sources;
        }
        for (PsiDirectory directory : psiPackage.getDirectories(module.getModuleScope(false))) {
            for (PsiFile file : directory.getFiles()) {
                VirtualFile virtualFile = file.getVirtualFile();
                if (file instanceof PsiJavaFile && virtualFile != null && !index.isInTestSourceContent(virtualFile)) {
                    sources.add(new CheckRequest.Source(virtualFile.getPath(), text(file, virtualFile)));
                }
            }
        }
        return sources;
    }

    /** The text the editor has: an open document's, unsaved edits included, else the file's. */
    private static String text(PsiFile file, VirtualFile virtualFile) {
        Document document = FileDocumentManager.getInstance().getCachedDocument(virtualFile);
        return document != null ? document.getText() : file.getText();
    }

    private static List<String> classpath(Module module) {
        return OrderEnumerator.orderEntries(module).withoutSdk().productionOnly().recursively().getPathsList()
                .getPathList();
    }

    /** The release of the main compilation: the module's bytecode target, else its language level. */
    private static int release(Project project, Module module) {
        String target = CompilerConfiguration.getInstance(project).getBytecodeTargetLevel(module);
        if (target != null && !target.isBlank()) {
            try {
                return Math.max(8, Integer.parseInt(target.startsWith("1.") ? target.substring(2) : target));
            } catch (NumberFormatException e) {
                // not a plain number: the language level decides
            }
        }
        return Math.max(8, LanguageLevelUtil.getEffectiveLanguageLevel(module).feature());
    }
}

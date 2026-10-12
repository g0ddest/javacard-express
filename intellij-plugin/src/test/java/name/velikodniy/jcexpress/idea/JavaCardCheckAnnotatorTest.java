package name.velikodniy.jcexpress.idea;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * The annotator in a light IDE project: a module with the Java Card API stubs and the toolkit's javac check on the
 * processor path of its compilation, as the Maven import gives a project set up by the build extension.
 */
public class JavaCardCheckAnnotatorTest extends LightJavaCodeInsightFixtureTestCase {

    private static final LightProjectDescriptor APPLET_MODULE = new DefaultLightProjectDescriptor() {
        /** A real JDK, the test runtime's: the mock JDK of the test framework lacks java.lang.RuntimeException. */
        @Override
        public Sdk getSdk() {
            return JavaSdk.getInstance().createJdk("test-runtime", System.getProperty("java.home"), false);
        }

        @Override
        public void configureModule(@NotNull Module module, @NotNull ModifiableRootModel model,
                                    @NotNull ContentEntry contentEntry) {
            super.configureModule(module, model, contentEntry);
            for (String jar : Applets.apiStubs()) {
                PsiTestUtil.addLibrary(model, "javacard-express-api", new File(jar).getParent(),
                        new File(jar).getName());
            }
            model.getModuleExtension(LanguageLevelModuleExtension.class).setLanguageLevel(LanguageLevel.JDK_1_8);
        }
    };

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return APPLET_MODULE;
    }

    @Override
    protected void setUp() throws Exception {
        // IntelliJ IDEA 2026.2 has one product; the start-up activity of its commercial part cannot be created in
        // a test run and logs an error when the test project opens. That error alone is let through.
        LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
            @Override
            public @NotNull Set<Action> processError(@NotNull String category, @NotNull String message,
                                                     String @NotNull [] details, @Nullable Throwable t) {
                return message.contains("[Plugin: com.intellij.modules.ultimate]") ? Action.NONE : Action.ALL;
            }
        }, super::setUp);
        ProcessorConfigProfile profile = profile();
        profile.setEnabled(true);
        profile.setObtainProcessorsFromClasspath(false);
        profile.setProcessorPath(String.join(File.pathSeparator, Applets.processorPath()));
        profile.clearProcessorOptions();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            if (getProject() == null) {
                return;
            }
            ProcessorConfigProfile profile = profile();
            profile.setEnabled(false);
            profile.setObtainProcessorsFromClasspath(true);
            profile.setProcessorPath("");
            profile.clearProcessorOptions();
        } catch (Throwable e) {
            addSuppressedException(e);
        } finally {
            super.tearDown();
        }
    }

    public void testALongFieldIsMarkedAtIt_jcvm31_2_2_1_3() {
        configure(Applets.WITH_LONG);

        List<HighlightInfo> errors = javaCardErrors();

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getDescription(),
                errors.get(0).getDescription().startsWith("Java Card: long type not supported"));
        assertTrue(marked(errors.get(0)), marked(errors.get(0)).contains("total"));
    }

    public void testAValidAppletHasNoJavaCardError() {
        configure(Applets.VALID);

        assertEmpty(javaCardErrors());
    }

    public void testAModuleWhoseCompilationHasNoCheckGetsNoJavaCardError() {
        profile().setProcessorPath("");
        configure(Applets.WITH_LONG);

        assertEmpty(javaCardErrors());
    }

    /** With classes in several packages the build checks those with an applet; host code stays unmarked. */
    public void testAHostPackageNextToTheAppletIsNotChecked() {
        PsiFile wallet = myFixture.addFileToProject("com/example/wallet/WalletApplet.java", Applets.WITH_LONG);
        PsiFile host = myFixture.addFileToProject("com/example/host/Tool.java",
                "package com.example.host;\n\npublic class Tool {\n    private long total;\n}\n");

        myFixture.configureFromExistingVirtualFile(host.getVirtualFile());
        assertEmpty(javaCardErrors());
        myFixture.configureFromExistingVirtualFile(wallet.getVirtualFile());
        assertEquals(1, javaCardErrors().size());
    }

    public void testCheckFalseTurnsTheMarksOffAsItTurnsTheCheckOff() {
        profile().setOption("javacard.check", "false");
        configure(Applets.WITH_LONG);

        assertEmpty(javaCardErrors());
    }

    public void testTheBuildsTargetPlatformDecides_jcvm31_4_5_2() {
        profile().setOption("javacard.version", "3.0.4");
        configure(Applets.WITH_ARRAY_FILL);

        List<HighlightInfo> errors = javaCardErrors();

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).getDescription(), errors.get(0).getDescription().contains("arrayFill"));
    }

    public void testAnUnsavedEditIsChecked() {
        configure(Applets.VALID);
        assertEmpty(javaCardErrors());

        int offset = myFixture.getEditor().getDocument().getText().indexOf("    private short balance;");
        myFixture.getEditor().getCaretModel().moveToOffset(offset);
        myFixture.type("    private long total;\n");

        List<HighlightInfo> errors = javaCardErrors();
        assertEquals(1, errors.size());
        assertTrue(marked(errors.get(0)), marked(errors.get(0)).contains("total"));
    }

    private ProcessorConfigProfile profile() {
        return ((CompilerConfigurationImpl) CompilerConfiguration.getInstance(getProject()))
                .getDefaultProcessorProfile();
    }

    private void configure(String text) {
        PsiFile file = myFixture.addFileToProject("com/example/wallet/WalletApplet.java", text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    }

    private List<HighlightInfo> javaCardErrors() {
        return myFixture.doHighlighting(HighlightSeverity.ERROR).stream()
                .filter(info -> info.getDescription() != null && info.getDescription().startsWith("Java Card: "))
                .toList();
    }

    private String marked(HighlightInfo info) {
        return myFixture.getEditor().getDocument().getText().substring(info.getStartOffset(), info.getEndOffset());
    }
}

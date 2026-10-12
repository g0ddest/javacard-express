package com.example.proc;

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

/** A project's own annotation processor: says that it ran, once per compilation, naming a class it saw. */
@SupportedAnnotationTypes("*")
public class OwnProcessor extends AbstractProcessor {

    private boolean said;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (Element root : round.getRootElements()) {
            if (!said) {
                said = true;
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "OWN-PROCESSOR-RAN for " + root);
            }
        }
        return false;
    }
}

package io.github.goodyuanwei

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import org.gradle.api.Project
import org.apache.commons.io.FileUtils

class TimeTransform extends Transform {

    private Project mProject

    TimeTransform(Project project) {
        this.mProject = project
    }

    @Override
    String getName() {
        return 'FuncTimeTransform'
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return true
    }

    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {

        TimeExtension timerExtension = mProject.extensions.getByName('tantan')

        def c = null
        def f = null

        if (timerExtension) {
            println "-------- timerExtension.handleClass is ${timerExtension.functime_c}"
            println "-------- timerExtension.handleMethod is ${timerExtension.functime_f}"
            c = timerExtension.functime_c
            f = timerExtension.functime_f
        }
        each {
            println("-------- timerExtension is null")
        }

        if (outputProvider != null) {
            outputProvider.deleteAll();
        }

        inputs.each {
            TransformInput input ->
                input.directoryInputs.each {
                    DirectoryInput directoryInput ->
                        LogInsertHelper.loadClassPath(directoryInput.file.absolutePath, mProject)
                }

                input.jarInputs.each {
                    JarInput jarInput ->
                        LogInsertHelper.loadClassPath(jarInput.file.absolutePath, mProject)
                }

                input.directoryInputs.each {
                    DirectoryInput directoryInput ->
                        def dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
                        if (c && f) {
                            LogInsertHelper.injectFunTime(directoryInput.file.absolutePath, c, f)
                        }
                        FileUtils.copyDirectory(directoryInput.file, dest)
                }

                input.jarInputs.each {
                    JarInput jarInput ->
                        def dest = outputProvider.getContentLocation(jarInput.name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                        FileUtils.copyFile(jarInput.file, dest)
                }
        }
    }
}

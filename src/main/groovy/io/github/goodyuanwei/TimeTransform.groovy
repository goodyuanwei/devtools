package io.github.goodyuanwei;

import com.android.SdkConstants
import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.Modifier
import org.gradle.api.Project
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

class TimeTransform extends Transform {
    Project mProject

    String loggerClass

    String[] packages

    TimeTransform(Project project) {
        this.mProject = project
    }

    @Override
    String getName() {
        return getClass().getSimpleName()
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
        ToolsExtension extension = mProject.extensions.getByName('develop')
        if (extension.hookPackage) {
            packages = extension.hookPackage.split(',')
            packages.eachWithIndex { String entry, int i ->
                packages[i] = entry.trim()
                println("------- package:${packages[i]}")
            }
        }
        loggerClass = extension.loggerClass
        if (!loggerClass || !packages) {
            return
        }

        if (outputProvider != null) {
            outputProvider.deleteAll();
        }

        inputs.each {
            TransformInput input ->
                input.directoryInputs.each { directoryInput ->
                    loadClassPath(directoryInput.file.absolutePath, mProject)
                }

                input.jarInputs.each { JarInput jarInput ->
                    loadClassPath(jarInput.file.absolutePath, mProject)
                }

                input.directoryInputs.each {
                    DirectoryInput directoryInput ->
                        def dest = outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
                        processDirFile(directoryInput.file.absolutePath)
                        FileUtils.copyDirectory(directoryInput.file, dest)
                }

                input.jarInputs.each {
                    JarInput jarInput ->
                        def dest = outputProvider.getContentLocation(jarInput.name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                        processJarFile(jarInput, dest)
                }
        }
    }

    ClassPool sClassPool = ClassPool.getDefault()

    void loadClassPath(String path, Project project) {
        sClassPool.appendClassPath(path)
        sClassPool.appendClassPath(project.android.bootClasspath[0].toString())
    }

    void processDirFile(String path) {

        File dir = new File(path)
        if (!dir.isDirectory()) {
            return
        }

        dir.eachFileRecurse { file ->
            def className = file.absolutePath.substring(dir.absolutePath.length() + 1).replace('/', '.')
            if (checkClassFile(className)) {
                boolean isCodeChanged = false
                CtClass ctClass = sClassPool.getCtClass(className.replace('.class', ""))

                if (ctClass.isFrozen()) {
                    ctClass.defrost()
                }
                ctClass.getDeclaredMethods().each { ctMethod ->
                    if (isFilterMethod(ctMethod)) {
                        isCodeChanged = modifyMethod(ctClass, ctMethod)
                    }
                }
                if (isCodeChanged) {
                    ctClass.writeFile(path)
                }
                ctClass.detach()
            }
        }
    }

    void processJarFile(JarInput jarInput, File dest) {
        if (!jarInput.file.path.endsWith('.jar')) {
            return
        }

        File newFile = new File(jarInput.file.getParent() + File.separator + "classes_temp.jar")
        if (newFile.exists()) {
            newFile.delete()
        }

        JarOutputStream jos = new JarOutputStream(new FileOutputStream(newFile))
        JarFile jarFile = new JarFile(jarInput.file)
        Enumeration enumeration = jarFile.entries()

        while (enumeration.hasMoreElements()) {
            JarEntry jarEntry = enumeration.nextElement()
            InputStream is = jarFile.getInputStream(jarEntry)
            def entryName = jarEntry.getName()
            def className = entryName.replace('/', '.')
            ZipEntry zipEntry = new ZipEntry(entryName)

            if (checkClassFile(className)) {
                jos.putNextEntry(zipEntry)
                CtClass ctClass = sClassPool.getCtClass(className.replace('.class', ""))

                if (ctClass.isFrozen()) {
                    ctClass.defrost()
                }

                ctClass.getDeclaredMethods().each { ctMethod ->
                    if (isFilterMethod(ctMethod)) {
                        modifyMethod(ctClass, ctMethod)
                    }
                }
                jos.write(ctClass.toBytecode())
            } else {
                jos.putNextEntry(zipEntry)
                jos.write(IOUtils.toByteArray(is))
            }

            jos.closeEntry()
        }

        jos.close()
        jarFile.close()

        FileUtils.copyFile(newFile, dest)
        newFile.delete()
    }

    boolean modifyMethod(CtClass ctClass, CtMethod ctMethod) {
        def success = true
        try {
            ctMethod.addLocalVariable("__hook_start_time__", CtClass.longType)
            ctMethod.insertBefore("__hook_start_time__ = System.currentTimeMillis();")
//            ctMethod.insertAfter("${loggerClass}.onFuncTime(\"${ctClass.name}\", \"${ctMethod.name}\", System.currentTimeMillis() - __hook_start_time__);")
            ctMethod.insertAfter("${loggerClass}.onFuncTime(\"${ctClass.name}\", \"${ctMethod.name}\", System.currentTimeMillis() - __hook_start_time__);")
            println("--------- hook ${ctClass.name}.${ctMethod.name}")
        }
        catch (e) {
            println("--------- fail ${ctClass.name}.${ctMethod.name} ${e.getMessage()}")
            success = false
        }
        return success
    }

    boolean checkClassFile(String name) {
        if (!name.endsWith('.class')) {
            return false
        }

        name = name.replace('.class', '')

        if (name == loggerClass) {
            return false
        }

        if (name.endsWith('.R')) {
            return false
        }

        if (name.contains('R\$')) {
            return false
        }

        if (name.endsWith('.BuildConfig')) {
            return false
        }

        def result = false
        for (def str in packages) {
            if (name.startsWith(str)) {
                println("------ checkClass ${name} true")
                result = true
                break
            }
        }

        return result
    }

    boolean isFilterMethod(CtMethod method) {
        if (method.isEmpty()) {
            return false
        }

        if (method.name.startsWith('access$')) {
            return false
        }

        if (Modifier.isNative(method.getModifiers())) {
            return false
        }

        String name = method.getName()
        if (name.contains("\$") && name.contains("isLoggable")) {
            return false
        }
        return true
    }
}

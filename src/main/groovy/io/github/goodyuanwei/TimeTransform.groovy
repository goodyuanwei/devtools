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
            c = timerExtension.functime_c
            f = timerExtension.functime_f
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
                        if (c && f) {
                            processDirFile(directoryInput.file.absolutePath, c, f)
                        }
                        FileUtils.copyDirectory(directoryInput.file, dest)
                }

                input.jarInputs.each {
                    JarInput jarInput ->
                        def dest = outputProvider.getContentLocation(jarInput.name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                        if (jarInput.name.contains('viewpager2')) {
                            println("--------- jarInput.path = ${jarInput.file.path}")
                            processJarFile(jarInput, dest)
                        }
                        each {
                            FileUtils.copyFile(jarInput.file, dest)
                        }
                }

        }
    }

    ClassPool sClassPool = ClassPool.getDefault()

    void loadClassPath(String path, Project project) {
        sClassPool.appendClassPath(path)
        sClassPool.appendClassPath(project.android.bootClasspath[0].toString())
    }

    void processDirFile(String path, String c, String f) {

        File dir = new File(path)
        if (!dir.isDirectory()) {
            return
        }

        dir.eachFileRecurse { File file ->
            if (checkClassFile(file.name)) {
                boolean isCodeChanged = false

                String tempStr = file.getCanonicalPath()
                String fullpath = tempStr.substring(dir.absolutePath.length() + 1, tempStr.length())
                String className = fullpath.replace("/", ".")

                if (className.endsWith(".class")) {
                    className = className.replace(".class", "")
                }

                CtClass ctClass = sClassPool.getCtClass(className)

                if (ctClass.isFrozen()) {
                    ctClass.defrost()
                }

                ctClass.getDeclaredMethods().each { ctMethod ->
                    if (isFilterMethod(ctMethod)) {
                        ctMethod.addLocalVariable("__hook_start_time__", CtClass.longType)
                        ctMethod.insertBefore("__hook_start_time__ = System.currentTimeMillis();")
                        ctMethod.insertAfter("${c}.${f}(\"${ctClass.name}\", \"${ctMethod.name}\", System.currentTimeMillis() - __hook_start_time__);")
                        isCodeChanged = true
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

            String entryName = jarEntry.getName()
            ZipEntry zipEntry = new ZipEntry(entryName)

            String[] classNames = entryName.split("/")
            String className = classNames[classNames.length - 1]

            if (classNames.length > 0 && checkJarFile(entryName) && checkClassFile(className)) {
                jos.putNextEntry(zipEntry)
                String simpleName = entryName.replace('/', '.').replace(SdkConstants.DOT_CLASS, '')
                CtClass ctClass = sClassPool.getCtClass(simpleName)

                if (ctClass.isFrozen()) {
                    ctClass.defrost()
                }

                ctClass.getDeclaredMethods().each { ctMethod ->
                    if (isFilterMethod(ctMethod)) {

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

    boolean checkJarFile(String name) {
//        return !name.startsWith("android") && !name.startsWith("javassist")
        return true
    }

    boolean checkClassFile(String name) {
        if (!name.endsWith('.class')) {
            return false
        }

        if (name.startsWith('R\$')) {
            return false
        }

        if (name == 'R.class' || name == 'BuildConfig.class' || name == 'FunctionTimer.class') {
            return false
        }
        return true
    }

    boolean isFilterMethod(CtMethod method) {
        if (method.isEmpty() || Modifier.isNative(method.getModifiers())) {
            return false
        }

        String name = method.getName()
        if (name.contains("\$") && name.contains("isLoggable")) {
            return false
        }
        return true
    }

}

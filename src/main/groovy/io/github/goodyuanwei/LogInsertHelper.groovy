package com.p1.developer

import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.Modifier
import org.gradle.api.Project

class LogInsertHelper {

    private static final ClassPool sClassPool = ClassPool.getDefault()

    static void loadClassPath(String path, Project project) {
        sClassPool.appendClassPath(path)
        sClassPool.appendClassPath(project.android.bootClasspath[0].toString())
//        sClassPool.importPackage('android.os.Bundle')
    }

    static void injectFunTime(String path, String c, String f) {

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
                CtClass ftClass = sClassPool.getCtClass(c)

                if (ctClass.isFrozen()) {
                    ctClass.defrost()
                }

                ctClass.getDeclaredMethods().each {
                    CtMethod ctMethod ->
                        if (!ctMethod.isEmpty() && !isNative(ctMethod) && filterMethodName(ctMethod)) {
//                            ctMethod.addLocalVariable('_hook_printer_', ftClass)
//                            ctMethod.insertBefore("_hook_printer_ = com.tantan.app.statics.FunctionTimer.getInstance();")
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

    static boolean checkClassFile(String name) {
        println "------ checkClassFile ${name}"

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

    static boolean isNative(CtMethod method) {
        return Modifier.isNative(method.getModifiers())
    }

    static boolean filterMethodName(CtMethod ctMethod) {
        String name = ctMethod.getName()
        if (name.contains("\$") && name.contains("isLoggable")) {
            return false
        }
        return true
    }

}

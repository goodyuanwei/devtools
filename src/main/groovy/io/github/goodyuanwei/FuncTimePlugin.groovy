package io.github.goodyuanwei

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin;
import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.AppExtension

class FuncTimePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create("develop", ToolsExtension.class)

        try {
            def android = project.extensions.getByType(AppExtension.class)
            println("--------- find plugin com.android.application ${project.name}")
            android.registerTransform(new TimeTransform(project))
        }
        catch (e) {
            println("---------- error ${e.message}")
        }
    }

}

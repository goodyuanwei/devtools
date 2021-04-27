package io.github.goodyuanwei;


import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.AppExtension

class FuncTimePlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.extensions.create("tantan", TimeExtension.class)
        def android = project.extensions.getByType(AppExtension)
        android.registerTransform(new TimeTransform(project))
    }
}

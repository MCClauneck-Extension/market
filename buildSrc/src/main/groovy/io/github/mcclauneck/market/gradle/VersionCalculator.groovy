package io.github.mcclauneck.market.gradle

import org.gradle.api.Project

class VersionCalculator {

    static String calculate(Project project) {
        String buildNum = System.getenv("BUILD_NUMBER")
        String devRelease = System.getenv("DEV_RELEASE_VERSION")
        String releaseTag = System.getenv("RELEASE_VERSION")
        
        boolean isDevBuild = (buildNum != null && !buildNum.isEmpty())
        boolean isDevReleaseBuild = (devRelease != null && !devRelease.isEmpty())

        String calculatedVersion = "unspecified"

        if (releaseTag != null && !releaseTag.isEmpty()) {
            calculatedVersion = releaseTag.replace("v", "")
        } else if (project.hasProperty('project-version')) {
            def baseVersion = project.property('project-version')
            def iteration = project.findProperty('project-iteration') ?: "1"
            
            if (isDevBuild || isDevReleaseBuild) {
                calculatedVersion = "${baseVersion}-build.${buildNum}"
            } else {
                calculatedVersion = "${baseVersion}-${iteration}"
            }
        }
        
        return calculatedVersion
    }
}

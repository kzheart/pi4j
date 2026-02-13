import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

allprojects {
    group = "com.pi4j"
    version = "1.0.3-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:5.10.2")
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    }

    extensions.configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                groupId = project.group.toString()
                artifactId = project.name
                version = project.version.toString()
            }
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

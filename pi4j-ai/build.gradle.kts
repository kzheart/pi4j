dependencies {
    api("com.google.code.gson:gson:2.11.0")
    api("org.slf4j:slf4j-api:1.7.25")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("software.amazon.awssdk:bedrockruntime:2.31.18")
}

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    java.srcDir("src/integrationTest/java")
    resources.srcDir("src/integrationTest/resources")
    compileClasspath += sourceSets["main"].output + configurations["testRuntimeClasspath"]
    runtimeClasspath += output + compileClasspath
}

configurations[integrationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations["testImplementation"])
configurations[integrationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations["testRuntimeOnly"])

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests that can hit real APIs."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
    shouldRunAfter(tasks.named("test"))
}

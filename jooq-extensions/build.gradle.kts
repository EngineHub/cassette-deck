plugins {
    `java-library`
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(16))
    withJavadocJar()
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

dependencies {
    api(libs.jooq)
    api(libs.jooq.meta)
    api(libs.jooq.codegen)
}

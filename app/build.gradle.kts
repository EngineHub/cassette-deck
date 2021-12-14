import org.cadixdev.gradle.licenser.LicenseExtension
import org.jooq.meta.jaxb.ForcedType
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    application
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.licenser)
    alias(libs.plugins.jooq)
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

repositories {
    mavenCentral()
}

configure<LicenseExtension> {
    exclude {
        // TODO make a PR to licenser to properly fix this
        it.file.startsWith(project.buildDir)
    }
    header(rootProject.file("HEADER.txt"))
    (this as ExtensionAware).extra.apply {
        for (key in listOf("organization", "url")) {
            set(key, rootProject.property(key))
        }
    }
}

dependencies {
    compileOnly(libs.jetbrains.annotations)

    implementation(libs.guava)

    implementation(libs.xerial.sqlite)
    jooqGenerator(libs.xerial.sqlite)
    jooqGenerator(project(":jooq-extensions"))

    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.security)

    implementation(libs.bucket4j.core)

    implementation(libs.apache.commons.compress)

    // Spring Boot Substitutions (+$1/each)
    implementation(libs.spring.boot.starter.undertow)
    implementation(libs.spring.boot.starter.log4j2)
    modules {
        module(libs.spring.boot.starter.tomcat.get().module) {
            replacedBy(libs.spring.boot.starter.undertow.get().module, "Tomcat bad.")
        }
        module(libs.spring.boot.starter.logging.get().module) {
            replacedBy(libs.spring.boot.starter.log4j2.get().module, "Log4J2 good.")
        }
    }

    // For Log4J2 async
    runtimeOnly(libs.disruptor)

    // Force 2.15
    constraints {
        add("implementation", "org.apache.logging.log4j:log4j-api") {
            version {
                strictly("[2.15, 3[")
                prefer("2.15.0")
            }
            because("CVE-2021-44228: Log4j vulnerable to remote code execution")
        }
        add("implementation", "org.apache.logging.log4j:log4j-core") {
            version {
                strictly("[2.15, 3[")
                prefer("2.15.0")
            }
            because("CVE-2021-44228: Log4j vulnerable to remote code execution")
        }
        add("implementation", "org.apache.logging.log4j:log4j-jul") {
            version {
                strictly("[2.15, 3[")
                prefer("2.15.0")
            }
            because("CVE-2021-44228: Log4j vulnerable to remote code execution")
        }
        add("implementation", "org.apache.logging.log4j:log4j-slf4j-impl") {
            version {
                strictly("[2.15, 3[")
                prefer("2.15.0")
            }
            because("CVE-2021-44228: Log4j vulnerable to remote code execution")
        }
    }

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

application {
    mainClass.set("org.enginehub.cassettedeck.CassetteDeck")
    applicationDefaultJvmArgs = listOf(
        "-Dlog4j2.contextSelector=org.apache.logging.log4j.core.async.AsyncLoggerContextSelector",
        "-Xms64M",
        "-Xmx512M",
        "-XX:G1PeriodicGCInterval=1000"
    )
}

tasks.named<JavaExec>("run") {
    workingDir(".")
}

tasks.register<Exec>("createDatabase") {
    val initSql = file("./src/main/sql/init.sql")
    inputs.file(initSql)
    outputs.file("./storage/database.sqlite")
    onlyIf { outputs.files.any { !it.exists() } }
    description = "Create the application database if it's missing"
    workingDir = file("./storage")
    commandLine("sqlite3", "database.sqlite", "-bail", "-init", initSql)
}

jooq {
    version.set(libs.versions.jooq)

    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.sqlite.JDBC"
                    url = "jdbc:sqlite:./storage/database.sqlite"
                }
                generator.apply {
                    name = "org.jooq.codegen.JavaGenerator"
                    database.apply {
                        name = "org.jooq.meta.sqlite.SQLiteDatabase"

                        forcedTypes.add(
                            ForcedType().apply {
                                name = "INSTANT"
                                includeExpression = "release_date"
                            }
                        )
                        forcedTypes.add(
                            ForcedType().apply {
                                name = "BOOLEAN"
                                includeExpression = "has_.*"
                            }
                        )
                    }
                    generate.apply {
                        isDeprecated = false
                        isDaos = true
                        isPojosAsJavaRecordClasses = true
                        isFluentSetters = false
                    }
                    target.apply {
                        packageName = "org.enginehub.cassettedeck.db.gen"
                    }
                    strategy.name = "org.enginehub.jooqext.codgen.EngineHubGeneratorStrategy"
                }
            }
        }
    }
}

tasks.named<nu.studer.gradle.jooq.JooqGenerate>("generateJooq") {
    dependsOn("createDatabase")
    inputs.file("./storage/database.sqlite")
    allInputsDeclared.set(true)
    setJavaExecSpec {
        executable = project.javaToolchains.launcherFor(java.toolchain).get().executablePath.asFile.absolutePath
    }
}


tasks.test {
    useJUnitPlatform()
}

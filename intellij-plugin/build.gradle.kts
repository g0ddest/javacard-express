import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "name.velikodniy"
// The version is set in one place, the toolkit's .mvn/maven.config (-Drevision=...): the plugin has the version of the
// toolkit, and its tests run the toolkit's javac check and API stubs of that version (Maven Central or ~/.m2)
val toolkitVersion: String = Regex("""-Drevision=(\S+)""")
    .find(rootDir.resolve("../.mvn/maven.config").readText())?.groupValues?.get(1)
    ?: error("no -Drevision=... in ../.mvn/maven.config")
version = toolkitVersion

repositories {
    mavenCentral()
    mavenLocal()
    intellijPlatform {
        defaultRepositories()
    }
}

// The toolkit's javac check (with the converter) and its API stubs: the tests run the check as a project does
val javacCheck: Configuration = configurations.create("javacCheck")
val apiStubs: Configuration = configurations.create("apiStubs")

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIde")
        if (localIde.isPresent) {
            local(localIde)
        } else {
            intellijIdea(providers.gradleProperty("platformVersion"))
        }
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.idea.maven")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")

    val toolkit = toolkitVersion
    javacCheck("name.velikodniy:javacard-express-javac-plugin:$toolkit")
    apiStubs("name.velikodniy:javacard-express-api:$toolkit") {
        isTransitive = false
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Xlint:-processing"))
}

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        id = "name.velikodniy.jcexpress"
        name = "JavaCard Express"
        version = project.version.toString()
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
        vendor {
            name = "Vitaliy Velikodniy"
            email = "vitaliy@velikodniy.name"
            url = "https://github.com/g0ddest/javacard-express"
        }
    }
    pluginVerification {
        ides {
            // the IDE the plugin is built against; recommended() would add the newer releases too
            create(IntelliJPlatformType.IntellijIdea, providers.gradleProperty("platformVersion"))
        }
    }
}

tasks.test {
    systemProperty("jcx.javacCheck", javacCheck.asPath)
    systemProperty("jcx.apiStubs", apiStubs.asPath)
    // the plugin never opens a reader; keep PC/SC unreachable in the test JVM as in the toolkit's own build
    jvmArgs("-Dsun.security.smartcardio.library=/nonexistent/jcx-no-pcsc")
}

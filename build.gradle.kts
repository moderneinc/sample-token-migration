plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

// Set as appropriate for your organization
group = "org.openrewrite.recipe"
description = "A sample of a migration from one authorization system to another."

// The bom version can also be set to a specific version or latest.release.
val latest = "latest.release"
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:${latest}"))

    implementation("org.openrewrite:rewrite-java")
    runtimeOnly("org.openrewrite:rewrite-java-17")

//    testImplementation("org.springframework:spring-web:5.1.+")
}

configure<PublishingExtension> {
    publications {
        named("nebula", MavenPublication::class.java) {
            suppressPomMetadataWarningsFor("runtimeElements")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "moderne"
            url = uri("https://us-west1-maven.pkg.dev/moderne-dev/moderne-recipe")
        }
    }
}

plugins {
    id("maven-publish")
}

publishing {
    publications {
        create<MavenPublication>("anoncredsUniffi") {
            groupId = "br.gov.serpro"
            artifactId = "anoncreds-uniffi"
            version = "1.0.0"

            artifact(file("$projectDir/anoncreds_uniffi-release.aar")) {
                extension = "aar"
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

//afterEvaluate {
//    publishing {
//        repositories {
//
//            // Repositório do Nexus (sempre presente)
//            maven {
//                name = "nexus"
//                url = uri("https://nexus.aic.serpro.gov.br/repository/snapshots/") // Altere conforme necessário
//                credentials {
//                    username = project.hasProperty("nexusUsername") ? project.getProperty("nexusUsername") : ""
//                    password = project.hasProperty("nexusPassword") ? project.getProperty("nexusPassword") : ""
//                }
//            }
//        }
//
//        publications {
//            snapshot(MavenPublication) {
//                from components.release
//                        groupId = "serpro"
//                artifactId = "ledger-besu-wrapper"
//                version = "1.0.0-SNAPSHOT"
//            }
//        }
//    }
//}
# Building secret-provider for Klarrio
* set the correct version in `gradle.properties`
* create/update `${GRADLE_USER_HOME}/gradle.properties` (typically, `~/.gradle/gradle.properties`)
 and assign the following variables:
  ```
   mavenUrl=https://klarrio.jfrog.io/klarrio/jvm-libs-local
   mavenUsername=<your jfrog username>
   mavenPassword=<your jfrog password, typically stored in ~/.ivy2/.klarrio-credentials>
  ```          
* follow the [README](README.md) to set up gradle
* NOTE: you can also forse the gradlewrapper to use gradle 6.x if you don't have it:
`./gradlew wrapper --gradle-version=6.5.1 --distribution-type=bin`
* run the command `./gradlew uploadArchives` to build and publish the secret-provider libraries.

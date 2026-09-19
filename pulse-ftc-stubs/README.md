# pulse-ftc-stubs

Compile-only stand-ins for FTC SDK and Android types so this repository can
`compileJava` and run JVM unit tests without the Android Gradle Plugin.

**Do not** add this module to a TeamCode project. Robot builds must compile
`pulse-ftc` against the official FTC SDK (`org.firstinspires.ftc:RobotCore`).

CI also compiles the adapters against RobotCore 11.2.0
(`./gradlew compileAgainstFtcSdk`). This module is **not** published.

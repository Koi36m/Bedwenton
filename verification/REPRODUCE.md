# Reproducing the first pass

Install a Java **25** JDK (not a JRE) and set `JAVA_HOME` and `PATH` to it.
The Gradle wrapper downloads Gradle 9.1.0 with a pinned SHA-256 checksum.

```sh
java -version
javac -version
./gradlew --version
./gradlew clean test
./gradlew check
```

The implementation was built with Oracle JDK 25.0.4.1 on Linux x86_64.
A different Java 25 JDK may be used for independent review.
Provisioning URLs used for this environment:

- https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz
- https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz.sha256
- https://downloads.gradle.org/distributions/gradle-9.1.0-bin.zip
- https://downloads.gradle.org/distributions/gradle-9.1.0-bin.zip.sha256

The JDK `latest` URL changes over time; compare the downloaded archive to its matching published checksum.
The exact archive hashes used for this pass are in `logs/toolchain-download-checksums.txt`.

In this managed cloud environment, the JDK and Gradle were extracted under
`/workspace/work/toolchains`. `GRADLE_USER_HOME=/workspace/work/gradle-home` kept writes in the workspace.
Network-enabled execution was needed for provisioning and Maven downloads.
Java/Gradle needed the session's proxy and trusted CA settings explicitly:

```sh
export GRADLE_OPTS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
```

Those paths and proxy settings are specific to the managed environment; configure your own environment normally.
TLS verification remained enabled. No credentials were required.

## Git checkpoint

The separate `minecraft-vfx-math-M1-rev3.git.bundle` contains all repository refs and history.
Restore it with:

```sh
git clone -b m1-core-transforms minecraft-vfx-math-M1-rev3.git.bundle minecraft-vfx-math
cd minecraft-vfx-math
git log --oneline --all --decorate
```

`main` and `baseline-scaffold` point to the scaffold; the implementation and evidence are on
`m1-core-transforms`. No final-acceptance tag exists.

## Input integrity

`input/minecraft-vfx-math-M1-handoff-rev3.zip` is the original uploaded archive.
Extract it separately, then run `sha256sum -c SHA256SUMS.txt` from its root.
The test and golden files in this source tree are unchanged from that archive.
`SOURCE-SHA256SUMS.txt` at the project root hashes all delivered project files except itself.

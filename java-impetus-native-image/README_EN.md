# java-impetus-native-image

[中文](README.md) | [English](README_EN.md) | [Project home](../README_EN.md)

![Status](https://img.shields.io/badge/Status-Experimental-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

An experimental Native Image scaffold, **excluded from the Java Impetus 2.0.0 release**. It is not a recommended application dependency.

## Current scope

The directory contains a minimal entry point and historical native-build configuration. It does not provide universal AOT/reflection metadata for the other modules or a complete native compatibility guarantee. Some POM image entry points are historical placeholders, not evidence of a working application image.

Build native executables in your own application using Spring Boot/GraalVM facilities and validate its actual reflection, dynamic queries, serialization and dependencies. This scaffold does not certify that other Java Impetus modules work in Native Image without configuration.

## Skills

There is no dedicated consumer skill for this scaffold. [Other module skills](../.agents/skills/README_EN.md) can guide JVM integration, but they **do not certify native compatibility**.

## License

[MIT License](../LICENSE).

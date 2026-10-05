# java-impetus-native-image

[中文](README.md) | [English](README_EN.md) | [项目首页](../README.md)

![Status](https://img.shields.io/badge/Status-Experimental-orange) [![MIT License](../.github/assets/license-mit.svg)](../LICENSE) [![DeepWiki](../.github/assets/deepwiki.svg)](https://deepwiki.com/catch-money/java-impetus)

本模块是 Native Image 的实验性脚手架，**不在 Java Impetus 2.0.0 的发布范围内**，不建议作为应用依赖。

## 当前范围

目录中只有最小入口与历史原生构建配置，不提供其他模块的统一 AOT／反射配置，也没有经过完整原生镜像兼容验证。POM 中部分镜像入口仍是历史占位配置，不能据此认为它可直接构建应用镜像。

需要原生部署时，在自己的应用中使用 Spring Boot／GraalVM 的原生构建能力，并针对实际使用的反射、动态查询、序列化和第三方依赖完成验证。本模块不承诺其他 Java Impetus 模块可未经配置直接用于 Native Image。

## Skills

目前没有本模块的使用方 skill。可在普通 JVM 接入时按需选择 [其他模块 skills](../.agents/skills/README.md)，但这些 skills **不代表 Native Image 兼容认证**。

## License

本模块使用 [MIT License](../LICENSE)。

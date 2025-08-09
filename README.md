
# Java Impetus ![Java](https://img.shields.io/badge/Java-21-orange?style=flat&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.3-brightgreen?style=flat&logo=spring-boot&logoColor=white) ![Maven](https://img.shields.io/badge/Maven-Build%20Tool-blue?style=flat&logo=apache-maven&logoColor=white) ![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg) [![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/catch-money/java-impetus)

> 基于 Java 21 和 Spring Boot 3.x 的企业级快速开发框架

Java Impetus 是一个专注于提供**语法糖式工具类**和**框架扩展封装**的企业级开发类库。它不对现有框架进行任务的修改，而是在原有基础上提供更便捷的 API 和工具，以减少开发过程中繁琐的配置和封装。


## [java-impetus-jpa](java-impetus-jpa)

java-impetus-jpa 是一个基于 JPA 的增强工具库，通过注解驱动的方式简化复杂查询的编写。它提供了丰富的查询注解、自动化的 Repository 管理、以及声明式的查询参数处理

使用方式和内容可查询文档[README.md](java-impetus-jpa/README.md)

## [java-impetus-spring-common](java-impetus-spring-common)
java-impetus-spring-common 封装了[Spring Boot](https://github.com/spring-projects/spring-boot) 开发过程中的一些常用操作

使用方式和内容可查阅文档[README.md](java-impetus-spring-common/README.md)

## [java-impetus-web-common](java-impetus-web-common)
java-impetus-web-common 封装了web开发过程中的跨域处理、异常统一处理、日志处理、消息处理等操作

使用方式和内容可查阅文档[README.md](java-impetus-web-common/README.md)

## [java-impetus-web-page](java-impetus-web-page)

java-impetus-web-page 基于[java-impetus-jpa](java-impetus-jpa)实现分页查询的统一处理,只需单个接口即可实现所有单表的分页查询

使用方式和内容可查阅文档[README.md](java-impetus-web-page/README.md)

## [java-impetus-common](java-impetus-common)
java-impetus-common 提供了开发过程中的一些常用的工具类

使用方式和内容可查阅文档[README.md](java-impetus-common/README.md)

## 🚀 快速开始

### 📋 环境要求

- **Java**: 21+
- **Spring Boot**: 3.x
- **Maven**: 3.8+

### 📥 版本管理

在你的 `pom.xml` 中添加依赖管理：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.jocker-cn</groupId>
            <artifactId>java-impetus-dependencies</artifactId>
            <version>1.1.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## 🐛 报告问题
如果你发现了 bug 或有功能建议，请在 [Issues](https://github.com/catch-money/java-impetus/issues) 中创建一个新的问题。

## 👨‍💻 作者
**jockerCN** - [GitHub](https://github.com/jocker-cn)

# License
Java Impetus 基于 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 开源协议。


# Java Impetus ![Java](https://img.shields.io/badge/Java-21-orange?style=flat&logo=openjdk&logoColor=white) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen?style=flat&logo=spring-boot&logoColor=white) ![Maven](https://img.shields.io/badge/Maven-Build%20Tool-blue?style=flat&logo=apache-maven&logoColor=white) ![License](https://img.shields.io/badge/License-MIT-blue.svg) ![Ask DeepWiki](https://deepwiki.com/catch-money/java-impetus)

> 基于 Java 21 和 Spring Boot 4.x 的企业级快速开发框架

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

## [java-impetus-toolkit](java-impetus-toolkit)
java-impetus-toolkit 是可选的 Java 工具集依赖入口，提供常用集合工具依赖、表达式解析及二维码、条形码工具

使用方式和内容可查阅文档[README.md](java-impetus-toolkit/README.md)

## [java-impetus-crypto](java-impetus-crypto)
java-impetus-crypto 提供独立的加密、哈希与旧密文兼容能力，不依赖 Spring

使用方式和内容可查阅文档[README.md](java-impetus-crypto/README.md)

## 模块 Skills

第三方项目可按依赖模块单独选用 skill；目录与复制说明见 [.agents/skills/README.md](.agents/skills/README.md)。目前已提供 [java-impetus-common skill](.agents/skills/java-impetus-common/SKILL.md)。

## 🚀 快速开始

### 📋 环境要求

- **Java**: 21+
- **Spring Boot**: 4.x
- **Maven**: 3.8+

### 本地构建与发布签名

普通构建及本地安装不执行 GPG 签名。BOM 不属于根 reactor，需要先单独安装；当前发布范围不包含 `java-impetus-native-image`：

```bash
mvn -f java-impetus-dependencies/pom.xml install
mvn -pl '!java-impetus-native-image' -am -DskipTests install
```

发布构建通过 `-Prelease` 显式启用签名，并在 `verify` 阶段生成签名文件，先于 `install` 和 `deploy`。因此 `-Prelease install` 仍会签名，但不会上传；Maven Central 发布不能跳过签名。`release` 是构建 profile，而不是 Maven 生命周期阶段。

签名私钥保存在开发机或 CI 的密钥库中，不得提交到仓库；Maven 的 `gpg.executable`、`gpg.homedir` 和 `gpg.keyname` 应与实际密钥一致。

发布脚本使用 `central-publishing-maven-plugin` 向 [Central Publisher Portal](https://central.sonatype.com) 发布，不再使用旧 OSSRH 地址或 `altDeploymentRepository`。先发布独立 BOM，再发布根项目和库模块；在仓库根目录执行：

```bash
bash deploy/deploy_dependencies.sh
bash deploy/deploy_impetus.sh
```

两个脚本都根据自身位置定位项目目录，不依赖调用时的工作目录，并保持 `-Prelease` 签名；第二个脚本排除 `java-impetus-native-image`。`settings.xml` 中 `sonatype` server 的凭据必须是 Portal 生成的 User Token。当前 `release` 配置启用 `autoPublish`，执行上述脚本会实际上传并在验证通过后自动公开发布，不是本地安装或演练。

### 📥 版本管理

在你的 `pom.xml` 中添加依赖管理：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.jocker-cn</groupId>
            <artifactId>java-impetus-dependencies</artifactId>
            <version>2.0.0</version>
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
Java Impetus 基于 [MIT License](LICENSE) 开源协议。

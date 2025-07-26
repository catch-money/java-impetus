# java-impetus-jpa ![Static Badge](https://img.shields.io/badge/spring_data_jpa-3.5.3-brightgreen?style=flat&logo=spring-boot&logoColor=white)
java-impetus-jpa 是对[spring-boot-starter-data-jpa](https://spring.io/projects/spring-data-jpa)的扩展.

该模块提供了注解驱动的单表查询方式和对Repository接口的自动化管理.
您可以以极为简单的方式完成单表的查询逻辑,不再需要针对不同的实体类(Entity)去实现不同的扩展接口,对于查询参数的添加和删除成本也非常低,让更多的精力放在业务功能开发上.

java-impetus-jpa 自动管理Entity实体对应的Repository接口,你可以通过该工具[JpaRepositoryUtils.java](src/main/java/io/github/jockerCN/jpa/autoRepository/JpaRepositoryUtils.java)直接获取实体对应的Repository接口,而不需要自己去实现.但是该接口只提供了原生的操作方法,如果你需要再接口中编写复杂的查询逻辑或是多表操作,也可以按照自己的习惯创建对应的Repository接口,这不与框架中所做的处理冲突.
但是Repository接口不能以`EntityClass.getSimpleName() + "AutoRepository"`的形式命名,他已被占用

如[PayEntity.java](src/test/java/io/github/jockerCN/entity/PayEntity.java), 再创建`PayEntityAutoRepository`名称的Repository接口是不被允许的.

## 快速开始

### 在你的 `pom.xml` 中添加依赖管理：

```xml
<!--Spring Data Jpa-->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
    <version>3.5.3</version>
</dependency>

<!--java-impetus-jpa-->
<dependency>
    <groupId>io.github.jocker-cn</groupId>
    <artifactId>java-impetus-jpa</artifactId>
    <version>1.1.0</version>
</dependency>

<!--数据库依赖,被Spring-Data-Jpa 支持的数据库均可,java-impetus-jpa不限制数据库类型-->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>9.3.0</version>
</dependency>
```

### 配置包扫描 [EnableAutoJpa.java](src/main/java/io/github/jockerCN/configuration/EnableAutoJpa.java)


```java
@EntityScan(basePackages = "io.github.jockerCN") // @EntityScan 指定查询参数的扫描路径
@ConfigurationPropertiesScan(basePackages = "io.github.jockerCN")
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```
### 编写数据库实体对象

- 示例：(请按照你自己项目的风格)
```java
package io.github.jockerCN.entity;


@Getter
@Setter
@ToString
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
@Entity(name = "PayEntity")
@Table(schema = "jpa", name = "pay")
public class PayEntity extends BaseJpaPojo {

    @Column(name = "pay_id", nullable = false, unique = true)
    private String payId;

    @Column(name = "pay_tmp_no", nullable = false, unique = true)
    private String payTmpNo;

    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;
    
    //.......
}
```

### 编写查询Param类
- 使用@JpaQuery 注解指定查询类对应的数据库实体
- 使用对应查询逻辑的注解,标注查询字段
```java
package io.github.jockerCN.query; //包路径与@EntityScan 中配置的保持一致

@JpaQuery(PayEntity.class)  //指定该查询参数对应的数据库实体
@Data
public class QueryTestParam {

    @Equals             //使用对应的查询逻辑注解 标注查询字段
    private String payId;
}
```

### 查询逻辑

```java
QueryTestParam param = new QueryTestParam();  //创建查询参数类
param.setPayId("PAY202405852383867"); //设置查询参数
List<PayEntity> queryList = JpaRepositoryUtils.queryList(param, PayEntity.class); //使用JpaRepositoryUtils查询api
```

### 确定你的查询参数被扫描到

- 启动时java-impetus-jpa会打印扫描到的查询参数类
```shell
2025-07-26T21:06:38.547+08:00  INFO 12260 --- [main] i.g.j.c.JpaQueryConfig :@JpaQuery Process class io.github.jockerCN.query.QueryTestParam
```


### 获取数据库实体对应的Repository接口

```java
JpaRepository<PayEntity, Long> jpaRepository = JpaRepositoryUtils.getJpaRepository(PayEntity.class);
```

## 支持的查询注解

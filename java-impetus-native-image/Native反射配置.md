```shell
java -agentlib:native-image-agent=config-output-dir=/path/to/save/config-files/ -jar your-app.jar
让需要获取反射配置的部分都跑一次,然后再指定目录自动生成reflect-config.json
```


```java
使用 @RegisterReflectionForBinding() 注解注册类;
```
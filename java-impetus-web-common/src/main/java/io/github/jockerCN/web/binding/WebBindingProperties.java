package io.github.jockerCN.web.binding;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties("java-impetus.web.binding")
public class WebBindingProperties {

    private boolean trimStrings;
    private List<String> datePatterns = List.of();
    private List<String> dateTimePatterns = List.of();
    private List<String> timePatterns = List.of();
    private List<String> offsetDateTimePatterns = List.of();
}

package io.github.jockerCN.web.binding;

import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

@ControllerAdvice
public class WebBindingAdvice {

    @InitBinder
    public void initializeBinder(WebDataBinder binder) {
        // PropertyEditor is mutable: a fresh instance is required for every binder.
        // Trimming must not change the caller's empty-string/null semantics.
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }
}

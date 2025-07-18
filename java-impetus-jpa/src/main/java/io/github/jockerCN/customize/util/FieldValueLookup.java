package io.github.jockerCN.customize.util;

import io.github.jockerCN.customize.exception.JpaProcessException;
import io.github.jockerCN.type.TypeConvert;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public class FieldValueLookup {


    private static final MethodHandles.Lookup lookup = MethodHandles.lookup();


    public static MethodHandle getMethodHandle(Field field, String annotation) {
        try {
            return lookup.unreflectGetter(field);
        } catch (IllegalAccessException e) {
            String errorMessage = String.format("Access to the field [%s] of class [%s] with annotation [%s] lookup methodHandle error:%s",
                    field.getName(), field.getDeclaringClass().getName(), annotation, e.getMessage());
            throw new JpaProcessException(errorMessage, e);
        }
    }


    public static <T> T invokeMethodHandle(MethodHandle methodHandle, Object obj, Field field, String annotation) {
        try {
            return TypeConvert.cast(methodHandle.invoke(obj));
        } catch (Throwable e) {
            String errorMessage = String.format("Error accessing field [%s] of class [%s] with annotation [%s]: %s",
                    field.getName(),
                    field.getDeclaringClass().getName(),
                    annotation,
                    e.getMessage());
            throw new JpaProcessException(errorMessage, e);
        }
    }


}

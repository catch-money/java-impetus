package io.github.jockerCN.validate;

import io.github.jockerCN.Result;
import io.github.jockerCN.annotation.AllowedValues;
import io.github.jockerCN.annotation.AtLeastOnePresent;
import io.github.jockerCN.annotation.EnumValue;
import io.github.jockerCN.annotation.FieldsEqual;
import io.github.jockerCN.annotation.UniqueElements;
import io.github.jockerCN.annotation.Validator;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BindException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationContractTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

    @AfterAll
    static void closeFactory() {
        FACTORY.close();
    }

    @Test
    void plainEnumsCanBeMatchedByNameOrBusinessProperty() {
        assertTrue(FACTORY.getValidator().validate(new EnumNameRequest("READY")).isEmpty());
        assertEquals("invalid state", FACTORY.getValidator().validate(new EnumNameRequest("MISSING"))
                .iterator().next().getMessage());
        assertTrue(FACTORY.getValidator().validate(new EnumCodeRequest(1)).isEmpty());
        assertFalse(FACTORY.getValidator().validate(new EnumCodeRequest(3)).isEmpty());
        assertTrue(FACTORY.getValidator().validate(new EnumCodesRequest(List.of(1, 2))).isEmpty());
        assertFalse(FACTORY.getValidator().validate(new EnumCodesRequest(List.of(1, 3))).isEmpty());
        assertTrue(FACTORY.getValidator().validate(new EnumObjectsRequest(new State[]{State.READY})).isEmpty());
        assertFalse(FACTORY.getValidator().validate(new EnumIterableRequest(Collections::emptyIterator)).isEmpty());
    }

    @Test
    void adaptersCoverAllowedValuesAndUniqueElements() {
        assertTrue(FACTORY.getValidator().validate(new AllowedRequest("ready")).isEmpty());
        assertFalse(FACTORY.getValidator().validate(new AllowedRequest("other")).isEmpty());
        assertTrue(FACTORY.getValidator().validate(new AllowedRequest(null)).isEmpty());
        assertTrue(FACTORY.getValidator().validate(new UniqueRequest(List.of("a", "b"))).isEmpty());
        assertFalse(FACTORY.getValidator().validate(new UniqueRequest(List.of("a", "a"))).isEmpty());
        assertFalse(FACTORY.getValidator().validate(new UniqueArrayRequest(new int[]{1, 1})).isEmpty());
        assertFalse(FACTORY.getValidator().validate(new UniqueRequest(Arrays.asList(null, null))).isEmpty());
        assertTrue(FACTORY.getValidator().validate(new OptionalUniqueRequest(Collections::emptyIterator)).isEmpty());
        assertThrows(ValidationException.class,
                () -> FACTORY.getValidator().validate(new InvalidAllowedTypeRequest(1)));
    }

    @Test
    void missingValueAndAdapterConfigurationAreExplicit() {
        assertFalse(FACTORY.getValidator().validate(new EnumNameRequest(null)).isEmpty());
        assertThrows(ValidationException.class, () -> FACTORY.getValidator().validate(new NoAdapterRequest("value")));
        assertThrows(ValidationException.class, () -> FACTORY.getValidator().validate(new InvalidEnumRequest("value")));
    }

    @Test
    void crossFieldConstraintsWorkForRecords() {
        assertTrue(FACTORY.getValidator().validate(new MatchingRequest("same", "same")).isEmpty());
        assertFalse(FACTORY.getValidator().validate(new MatchingRequest("one", "two")).isEmpty());
        assertTrue(FACTORY.getValidator().validate(new MatchingRequest(null, null)).isEmpty());
        assertTrue(FACTORY.getValidator().validate(new ContactRequest(null, "123")).isEmpty());
        assertFalse(FACTORY.getValidator().validate(new ContactRequest("  ", null)).isEmpty());
        assertTrue(FACTORY.getValidator().validate(new PlainContact("a@example.com", null)).isEmpty());
        assertThrows(ValidationException.class,
                () -> FACTORY.getValidator().validate(new InvalidPropertiesRequest("value")));
        assertThrows(ValidationException.class,
                () -> FACTORY.getValidator().validate(new InvalidContactRequest("value")));
    }

    @Test
    void programmaticValidationUsesInterpolatedMessagesAndGroups() {
        Result<Void> invalid = ValidationUtil.validateObject(new GroupRequest(""), Create.class);
        assertEquals("name is required", invalid.getMessage());
        assertEquals(List.of("name is required"), ValidationUtil.validateMessages(new GroupRequest(""), Create.class));
        assertThrows(BindException.class, () -> ValidationUtil.validate(new GroupRequest(""), Create.class));
        assertTrue(ValidationUtil.validateObject(new GroupRequest("ok"), Create.class).isOk());
    }

    public enum State {
        READY(1), CLOSED(2);

        private final int code;

        State(int code) {
            this.code = code;
        }

        public int code() {
            return code;
        }
    }

    private record EnumNameRequest(@EnumValue(enumType = State.class, message = "invalid state") String state) {
    }

    private record EnumCodeRequest(@EnumValue(enumType = State.class, property = "code") int state) {
    }

    private record EnumCodesRequest(@EnumValue(enumType = State.class, property = "code") List<Integer> states) {
    }

    private record EnumObjectsRequest(@EnumValue(enumType = State.class) State[] states) {
    }

    private record EnumIterableRequest(@EnumValue(enumType = State.class) Iterable<String> states) {
    }

    private record AllowedRequest(@AllowedValues(value = {"READY", "CLOSED"},
            ignoreCase = true, required = false) String value) {
    }

    private record UniqueRequest(@UniqueElements List<String> values) {
    }

    private record UniqueArrayRequest(@UniqueElements int[] values) {
    }

    private record OptionalUniqueRequest(@UniqueElements(required = false) Iterable<String> values) {
    }

    private record InvalidAllowedTypeRequest(@AllowedValues("READY") Integer value) {
    }

    private record NoAdapterRequest(@Validator String value) {
    }

    private record InvalidEnumRequest(@EnumValue(enumType = State.class, property = "missing") String value) {
    }

    @FieldsEqual(first = "password", second = "confirmation")
    private record MatchingRequest(String password, String confirmation) {
    }

    @AtLeastOnePresent({"email", "phone"})
    private record ContactRequest(String email, String phone) {
    }

    @AtLeastOnePresent({"email", "phone"})
    private static final class PlainContact {
        private final String email;
        private final String phone;

        private PlainContact(String email, String phone) {
            this.email = email;
            this.phone = phone;
        }
    }

    @FieldsEqual(first = "value", second = "missing")
    private record InvalidPropertiesRequest(String value) {
    }

    @AtLeastOnePresent({"value", "missing"})
    private record InvalidContactRequest(String value) {
    }

    private interface Create {
    }

    private record GroupRequest(@NotBlank(message = "name is required", groups = Create.class) String name) {
    }
}

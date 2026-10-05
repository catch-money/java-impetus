package io.github.jockerCN;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void existingFactoriesUseCanonicalCodes() {
        assertTrue(Result.ok("value").isOk());
        assertFalse(Result.ok().isError());
        assertEquals(401, Result.failWithTokenError().getCode());
        assertTrue(Result.failWithTokenError().isError());
        assertEquals(403, Result.failWithNoPermission().getCode());
        assertEquals(500, Result.failWithServerError().getCode());
        assertEquals(500, Result.fail().getCode());
        assertEquals(404, Result.failWithNotFound().getCode());
        assertEquals(3001, Result.failWithLocked().getCode());
    }

    @Test
    void statusCodesAreUnique() {
        var codes = Arrays.stream(Result.StatusCode.values())
                .map(Result.StatusCode::getCode)
                .collect(Collectors.toSet());
        assertEquals(Result.StatusCode.values().length, codes.size());
    }

    @Test
    void statusFactoriesSupportStandardErrorsWithoutChangingHttpTransport() {
        Result<String> conflict = Result.with("record", Result.StatusCode.CONFLICT);
        assertEquals("record", conflict.getData());
        assertEquals(409, conflict.getCode());
        assertEquals("Conflict", conflict.getMessage());
        assertTrue(conflict.isError());

        Result<Void> notFound = Result.with(Result.StatusCode.RESOURCE_NOT_FOUND);
        assertEquals(404, notFound.getCode());
        assertEquals("Not Found", notFound.getMessage());

        Result<String> custom = Result.with("input", Result.StatusCode.BAD_REQUEST, "invalid field");
        assertEquals(400, custom.getCode());
        assertEquals("invalid field", custom.getMessage());
    }

    @Test
    void noArgsConstructorAndSettersSupportBeanDeserialization() {
        Result<String> result = new Result<>();
        result.setData("updated");
        result.setCode(200);
        result.setMessage("done");
        assertEquals("updated", result.getData());
        assertTrue(result.isOk());
    }
}

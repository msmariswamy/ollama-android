package com.ollama.mobile.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkResultTest {

    @Test
    public void testSuccessResult() {
        NetworkResult<Integer> result = NetworkResult.success(42);
        assertEquals(NetworkResult.Status.SUCCESS, result.status);
        assertEquals(Integer.valueOf(42), result.data);
        assertNull(result.error);
    }

    @Test
    public void testErrorResult() {
        NetworkResult<String> result = NetworkResult.error("timeout");
        assertEquals(NetworkResult.Status.ERROR, result.status);
        assertNull(result.data);
        assertEquals("timeout", result.error);
    }

    @Test
    public void testLoadingResult() {
        NetworkResult<Object> result = NetworkResult.loading();
        assertEquals(NetworkResult.Status.LOADING, result.status);
        assertNull(result.data);
        assertNull(result.error);
    }

    @Test
    public void testSuccessIsNotError() {
        NetworkResult<String> success = NetworkResult.success("ok");
        NetworkResult<String> error = NetworkResult.error("fail");
        assertEquals(NetworkResult.Status.SUCCESS, success.status);
        assertEquals(NetworkResult.Status.ERROR, error.status);
        assertNotEquals(success.status, error.status);
    }
}

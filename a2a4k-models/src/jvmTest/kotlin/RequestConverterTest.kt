// SPDX-FileCopyrightText: 2025
//
// SPDX-License-Identifier: Apache-2.0
package io.github.a2a_4k.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RequestConverterTest {

    @Test
    fun `test fromJson with valid CancelTaskRequest`() {
        // Given
        val json = """
            {
                "jsonrpc": "2.0",
                "id": "123",
                "method": "tasks/cancel",
                "params": {
                    "id": "my-job-123",
                    "metadata": {}
                }
            }
        """.trimIndent()

        // When
        val result = json.toJsonRpcRequest()

        // Then
        assertIs<CancelTaskRequest>(result)
        assertEquals("2.0", result.jsonrpc)
        assertEquals("123", (result.id as StringValue).value)
        assertEquals("my-job-123", result.params.id)
    }

    @Test
    fun `test fromJson with valid GetTaskRequest`() {
        // Given
        val json = """
            {
                "jsonrpc": "2.0",
                "id": "456",
                "method": "tasks/get",
                "params": {
                    "id": "my-job-456",
                    "historyLength": 10,
                    "metadata": {}
                }
            }
        """.trimIndent()

        // When
        val result = json.toJsonRpcRequest()

        // Then
        assertIs<GetTaskRequest>(result)
        assertEquals("2.0", result.jsonrpc)
        assertEquals("456", (result.id as StringValue).value)
        assertEquals("my-job-456", result.params.id)
        assertEquals(10, result.params.historyLength)
    }

    @Test
    fun `test fromJson with unsupported method`() {
        // Given
        val json = """
            {
                "jsonrpc": "2.0",
                "id": "789",
                "method": "unsupported_method",
                "params": {}
            }
        """.trimIndent()

        // When/Then

        val result = json.toJsonRpcRequest()
        assertIs<UnknownMethodRequest>(result)
    }

    @Test
    fun `test fromJson with missing method field`() {
        // Given
        val json = """
            {
                "jsonrpc": "2.0",
                "id": "789",
                "params": {}
            }
        """.trimIndent()

        // When/Then

        val result = json.toJsonRpcRequest()
        assertIs<UnknownMethodRequest>(result)
    }

    @Test
    fun `test fromJson with invalid JSON`() {
        // Given
        val json = """
            {
                "jsonrpc": "2.0",
                "id": "789",
                "method": "tasks/get",
                "params": {
                    "id": "my-job-789"
                },
            }
        """.trimIndent()

        // When/Then
        assertThrows<IllegalArgumentException> {
            json.toJsonRpcRequest()
        }
    }
}

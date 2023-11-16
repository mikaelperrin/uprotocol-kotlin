/*
 * Copyright (c) 2023 General Motors GTO LLC
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.uprotocol.rpc

import com.google.protobuf.Any
import org.junit.jupiter.api.Assertions.*

import com.google.protobuf.Int32Value

import com.google.protobuf.InvalidProtocolBufferException

import com.google.rpc.Code

import com.google.rpc.Status
import io.cloudevents.v1.proto.CloudEvent

import org.eclipse.uprotocol.transport.builder.UAttributesBuilder

import org.eclipse.uprotocol.transport.datamodel.UPayload

import org.eclipse.uprotocol.uri.serializer.LongUriSerializer

import org.eclipse.uprotocol.v1.*

import org.junit.jupiter.api.DisplayName

import org.junit.jupiter.api.Test


import java.util.concurrent.CompletableFuture

import java.util.concurrent.ExecutionException


internal class RpcTest {
    private var returnsNumber3: RpcClient = object : RpcClient {

        override fun invokeMethod(topic: UUri, payload: UPayload, attributes: UAttributes): CompletableFuture<UPayload> {
            val data = UPayload(Any.pack(Int32Value.of(3)).toByteArray(), UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF)
            return CompletableFuture.completedFuture(data)
        }
    }
    private var happyPath: RpcClient = object : RpcClient {
        override fun invokeMethod(topic: UUri, payload: UPayload, attributes: UAttributes): CompletableFuture<UPayload> {
            val data: UPayload = buildUPayload()
            return CompletableFuture.completedFuture(data)
        }
    }
    private var withStatusCodeInsteadOfHappyPath: RpcClient = object : RpcClient {

        override fun invokeMethod(topic: UUri, payload: UPayload, attributes: UAttributes): CompletableFuture<UPayload> {
            val status: Status = Status.newBuilder().setCode(Code.INVALID_ARGUMENT_VALUE).setMessage("boom").build()
            val any: Any = Any.pack(status)
            val data = UPayload(any.toByteArray(), UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF)
            return CompletableFuture.completedFuture(data)
        }
    }
    private var withStatusCodeHappyPath: RpcClient = object : RpcClient {

        override fun invokeMethod(topic: UUri, payload: UPayload, attributes: UAttributes): CompletableFuture<UPayload> {
            val status: Status = Status.newBuilder().setCode(Code.OK_VALUE).setMessage("all good").build()
            val any: Any = Any.pack(status)
            val data = UPayload(any.toByteArray(), UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF)
            return CompletableFuture.completedFuture(data)
        }
    }
    private var thatBarfsCrapyPayload: RpcClient = object : RpcClient {

        override fun invokeMethod(topic: UUri, payload: UPayload, attributes: UAttributes): CompletableFuture<UPayload> {
            val response = UPayload(byteArrayOf(0), UPayloadFormat.UPAYLOAD_FORMAT_RAW)
            return CompletableFuture.completedFuture(response)
        }
    }
    private var thatCompletesWithAnException: RpcClient = object : RpcClient {

        override fun invokeMethod(topic: UUri, payload: UPayload, attributes: UAttributes): CompletableFuture<UPayload> {
            return CompletableFuture.failedFuture(RuntimeException("Boom"))
        }
    }
    private var thatReturnsTheWrongProto: RpcClient = object : RpcClient {

        override fun invokeMethod(topic: UUri, payload: UPayload, attributes: UAttributes): CompletableFuture<UPayload> {
            val any: Any = Any.pack(Int32Value.of(42))
            return CompletableFuture.completedFuture(
                UPayload(
                    any.toByteArray(),
                    UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF
                )
            )
        }
    }
    private var withNullInPayload: RpcClient = object : RpcClient {

        override fun invokeMethod(topic: UUri, payload: UPayload, attributes: UAttributes): CompletableFuture<UPayload> {
            return CompletableFuture.completedFuture(null)
        }
    }

    @Test
    fun test_compose_happy_path() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<Int32Value>> = RpcMapper.mapResponseToResult(
            returnsNumber3.invokeMethod(buildTopic(), payload, buildUAttributes()), Int32Value::class.java
        )
            .thenApply { ur -> ur.map { i -> Int32Value.of(i.value + 5) } }.exceptionally { exception ->
                println("in exceptionally")
                RpcResult.failure("boom", exception)
            }
        assertFalse(rpcResponse.isCompletedExceptionally())
        val test: CompletableFuture<Void> = rpcResponse.thenAccept { rpcResult ->
            assertTrue(rpcResult.isSuccess)
            assertEquals(Int32Value.of(8), rpcResult.successValue)
        }
        assertFalse(test.isCompletedExceptionally())
    }

    @Test
    @Throws(ExecutionException::class, InterruptedException::class)
    fun test_compose_that_returns_status() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<Int32Value>> = RpcMapper.mapResponseToResult(
            withStatusCodeInsteadOfHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
            Int32Value::class.java
        ).thenApply { ur -> ur.map { i -> Int32Value.of(i.value + 5) } }
            .exceptionally { exception ->
                println("in exceptionally")
                RpcResult.failure("boom", exception)
            }
        assertFalse(rpcResponse.isCompletedExceptionally())
        val test: CompletableFuture<Void> = rpcResponse.thenAccept { rpcResult ->
            assertTrue(rpcResult.isFailure)
            assertEquals(Code.INVALID_ARGUMENT_VALUE, rpcResult.failureValue.code)
            assertEquals("boom", rpcResult.failureValue.message)
        }
        assertFalse(test.isCompletedExceptionally())
        assertEquals(rpcResponse.get().failureValue.code, Code.INVALID_ARGUMENT_VALUE)
        assertFalse(test.isCompletedExceptionally())
    }

    @Test
    fun test_compose_with_failure() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<Int32Value>> = RpcMapper.mapResponseToResult(
                thatCompletesWithAnException.invokeMethod(buildTopic(), payload, buildUAttributes()),
            Int32Value::class.java
        )
            .thenApply { ur -> ur.map { i -> Int32Value.of(i.value + 5) } }
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(exception.message, "java.lang.RuntimeException: Boom")
    }

    @Test
    fun test_compose_with_failure_transform_Exception() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<Int32Value>> = RpcMapper.mapResponseToResult(
                thatCompletesWithAnException.invokeMethod(buildTopic(), payload, buildUAttributes()),
            Int32Value::class.java
        )
            .thenApply { ur -> ur.map { i -> Int32Value.of(i.value + 5) } }.exceptionally { exception ->
                println("in exceptionally")
                RpcResult.failure("boom", exception)
            }
        assertFalse(rpcResponse.isCompletedExceptionally())
        val test: CompletableFuture<Void> = rpcResponse.thenAccept { rpcResult ->
            assertTrue(rpcResult.isFailure)
            assertEquals(Code.UNKNOWN_VALUE, rpcResult.failureValue.code)
            assertEquals("boom", rpcResult.failureValue.message)
        }
        assertFalse(test.isCompletedExceptionally())
    }

    @Test
    fun test_success_invoke_method_happy_flow_using_mapResponseToRpcResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<CloudEvent>> =
                RpcMapper.mapResponseToResult(
                        happyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
                        CloudEvent::class.java
                )
        assertFalse(rpcResponse.isCompletedExceptionally())
        val test: CompletableFuture<Void> = rpcResponse.thenAccept { rpcResult ->
            assertTrue(rpcResult.isSuccess)
            assertEquals(buildCloudEvent(), rpcResult.successValue)
        }
        assertFalse(test.isCompletedExceptionally())
    }

    @Test
    fun test_fail_invoke_method_when_invoke_method_returns_a_status_using_mapResponseToRpcResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<CloudEvent>> =
            RpcMapper.mapResponseToResult(
                withStatusCodeInsteadOfHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
                CloudEvent::class.java
            )
        assertFalse(rpcResponse.isCompletedExceptionally())
        val test: CompletableFuture<Void> = rpcResponse.thenAccept { rpcResult ->
            assertTrue(rpcResult.isFailure)
            assertEquals(Code.INVALID_ARGUMENT.number, rpcResult.failureValue.code)
            assertEquals("boom", rpcResult.failureValue.message)
        }
        assertFalse(test.isCompletedExceptionally())
    }

    @Test
    fun test_fail_invoke_method_when_invoke_method_threw_an_exception_using_mapResponseToRpcResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<CloudEvent>> =
            RpcMapper.mapResponseToResult(
                thatCompletesWithAnException.invokeMethod(buildTopic(), payload, buildUAttributes()),
                CloudEvent::class.java
            )
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(exception.message, "java.lang.RuntimeException: Boom")
    }

    @Test
    fun test_fail_invoke_method_when_invoke_method_returns_a_bad_proto_using_mapResponseToRpcResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<CloudEvent>> =
            RpcMapper.mapResponseToResult(
                thatReturnsTheWrongProto.invokeMethod(buildTopic(), payload, buildUAttributes()),
                CloudEvent::class.java
            )
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(
            exception.message,
            "java.lang.RuntimeException: Unknown payload type [type.googleapis.com/google.protobuf.Int32Value]. " +
                    "Expected [io.cloudevents.v1.proto.CloudEvent]"
        )
    }

    @Test
    fun test_success_invoke_method_happy_flow_using_mapResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<CloudEvent> = RpcMapper.mapResponse(
            happyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
            CloudEvent::class.java
        )
        assertFalse(rpcResponse.isCompletedExceptionally())
        val test: CompletableFuture<Void> =
            rpcResponse.thenAccept { cloudEvent -> assertEquals(buildCloudEvent(), cloudEvent) }
        assertFalse(test.isCompletedExceptionally())
    }

    @Test
    fun test_fail_invoke_method_when_invoke_method_returns_a_status_using_mapResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<CloudEvent> = RpcMapper.mapResponse(
            withStatusCodeInsteadOfHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()),
            CloudEvent::class.java
        )
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(
            exception.message,
            "java.lang.RuntimeException: Unknown payload type [type.googleapis.com/google.rpc.Status]. Expected " +
                    "[io.cloudevents.v1.proto.CloudEvent]"
        )
    }

    @Test
    fun test_fail_invoke_method_when_invoke_method_threw_an_exception_using_mapResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<CloudEvent> = RpcMapper.mapResponse(
            thatCompletesWithAnException.invokeMethod(buildTopic(), payload, buildUAttributes()),
            CloudEvent::class.java
        )
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(exception.message, "java.lang.RuntimeException: Boom")
    }

    @Test
    fun test_fail_invoke_method_when_invoke_method_returns_a_bad_proto_using_mapResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<CloudEvent> = RpcMapper.mapResponse(
            thatReturnsTheWrongProto.invokeMethod(buildTopic(), payload, buildUAttributes()),
            CloudEvent::class.java
        )
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(
            exception.message,
            "java.lang.RuntimeException: Unknown payload type [type.googleapis.com/google.protobuf.Int32Value]. " +
                    "Expected [io.cloudevents.v1.proto.CloudEvent]"
        )
    }

    @Test
    fun test_success_invoke_method_happy_flow() {
        //Stub code
        val data: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<UPayload> = happyPath.invokeMethod(buildTopic(), data, buildUAttributes())
        val stubReturnValue: CompletableFuture<CloudEvent> =
            rpcResponse.handle { payload, exception ->
                val any: Any
                assertTrue(true)
                assertFalse(true)
                try {
                    any = Any.parseFrom(payload.data)
                    // happy flow, no exception
                    assertNull(exception)

                    // check the payload is not google.rpc.Status
                    assertFalse(any.`is`(Status::class.java))

                    // check the payload is the cloud event we build
                    assertTrue(any.`is`(CloudEvent::class.java))
                    return@handle any.unpack(CloudEvent::class.java)
                } catch (e: InvalidProtocolBufferException) {
                    throw RuntimeException(e)
                }
            }
        stubReturnValue.thenAccept { cloudEvent -> assertEquals(buildUPayload(), cloudEvent) }
    }

    @Test
    fun test_fail_invoke_method_when_invoke_method_returns_a_status() {
        //Stub code
        val data: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<UPayload> = withStatusCodeInsteadOfHappyPath.invokeMethod(
            buildTopic(),
            data, buildUAttributes()
        )
        val stubReturnValue: CompletableFuture<CloudEvent> =
            rpcResponse.handle { payload, exception ->
                try {
                    val any: Any = Any.parseFrom(payload.data)
                    // happy flow, no exception
                    assertNull(exception)

                    // check the payload not google.rpc.Status
                    assertTrue(any.`is`(Status::class.java))

                    // check the payload is not the type we expected
                    assertFalse(any.`is`(CloudEvent::class.java))

                    // we know it is a Status - so let's unpack it
                    val status: Status = any.unpack(Status::class.java)
                    throw RuntimeException(
                        String.format(
                            "Error returned, status code: [%s], message: [%s]",
                            Code.forNumber(status.code), status.message
                        )
                    )
                } catch (e: InvalidProtocolBufferException) {
                    throw RuntimeException(e)
                }
            }
        assertTrue(stubReturnValue.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, stubReturnValue::get)
        assertEquals(
            exception.message,
            "java.lang.RuntimeException: Error returned, status code: [INVALID_ARGUMENT], message: [boom]"
        )
    }

    @Test
    fun test_fail_invoke_method_when_invoke_method_threw_an_exception() {
        //Stub code
        val data: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<UPayload> = thatCompletesWithAnException.invokeMethod(
            buildTopic(), data,
            buildUAttributes()
        )
        val stubReturnValue: CompletableFuture<CloudEvent> =
            rpcResponse.handle { payload, exception ->
                // exception was thrown
                assertNotNull(exception)
                assertNull(payload)
                throw RuntimeException(exception.message, exception)
            }
        assertTrue(stubReturnValue.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, stubReturnValue::get)
        assertEquals(exception.message, "java.lang.RuntimeException: Boom")
    }

    @Test
    fun test_fail_invoke_method_when_invoke_method_returns_a_bad_proto() {
        //Stub code
        val data: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<UPayload> = thatReturnsTheWrongProto.invokeMethod(
            buildTopic(), data,
            buildUAttributes()
        )
        val stubReturnValue: CompletableFuture<CloudEvent> =
            rpcResponse.handle { payload, exception ->
                try {
                    val any: Any = Any.parseFrom(payload.data)
                    // happy flow, no exception
                    assertNull(exception)

                    // check the payload is not google.rpc.Status
                    assertFalse(any.`is`(Status::class.java))

                    // check the payload is the cloud event we build
                    assertFalse(any.`is`(CloudEvent::class.java))
                    return@handle any.unpack(CloudEvent::class.java)
                } catch (e: InvalidProtocolBufferException) {
                    throw RuntimeException(
                        String.format("%s [%s]", e.message, "io.cloudevents.v1.proto.CloudEvent.class"),
                        e
                    )
                }
            }
        assertTrue(stubReturnValue.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, stubReturnValue::get)
        assertEquals(
            exception.message,
            "java.lang.RuntimeException: Type of the Any message does not match the given class. [io.cloudevents" +
                    ".v1.proto.CloudEvent.class]"
        )
    }

    @Test
    @DisplayName("Invoke method that returns successfully with null in the payload")
    fun test_success_invoke_method_that_has_null_payload_mapResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<CloudEvent> = RpcMapper.mapResponse(
            withNullInPayload.invokeMethod(buildTopic(), payload, buildUAttributes()),
            CloudEvent::class.java
        )
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(
            exception.message,
            "java.lang.RuntimeException: Server returned a null payload. Expected io.cloudevents.v1.proto" +
                    ".CloudEvent"
        )
    }

    @Test
    @DisplayName("Invoke method that returns successfully with null in the payload, mapResponseToResult")
    fun test_success_invoke_method_that_has_null_payload_mapResponseToResultToRpcResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<CloudEvent>> =
            RpcMapper.mapResponseToResult(
                withNullInPayload.invokeMethod(buildTopic(), payload, buildUAttributes()),
                CloudEvent::class.java
            )
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(
            exception.message,
            "java.lang.RuntimeException: Server returned a null payload. Expected io.cloudevents.v1.proto" +
                    ".CloudEvent"
        )
    }

    @Test
    @DisplayName("Invoke method that expects a Status payload and returns successfully with OK Status in the payload")
    fun test_success_invoke_method_happy_flow_that_returns_status_using_mapResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<Status> = RpcMapper.mapResponse(
            withStatusCodeHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()), Status::class.java
        )
        assertFalse(rpcResponse.isCompletedExceptionally())
        val test: CompletableFuture<Void> = rpcResponse.thenAccept { status ->
            assertEquals(Code.OK.number, status.code)
            assertEquals("all good", status.message)
        }
        assertFalse(test.isCompletedExceptionally())
    }

    @Test
    @DisplayName(
        "Invoke method that expects a Status payload and returns successfully with OK Status in the payload," +
                " mapResponseToResult"
    )
    fun test_success_invoke_method_happy_flow_that_returns_status_using_mapResponseToResultToRpcResponse() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<Status>> = RpcMapper.mapResponseToResult(
            withStatusCodeHappyPath.invokeMethod(buildTopic(), payload, buildUAttributes()), Status::class.java
        )
        assertFalse(rpcResponse.isCompletedExceptionally())
        val test: CompletableFuture<Void> = rpcResponse.thenAccept { rpcResult ->
            assertTrue(rpcResult.isSuccess)
            assertEquals(Code.OK.number, rpcResult.successValue.code)
            assertEquals("all good", rpcResult.successValue.message)
        }
        assertFalse(test.isCompletedExceptionally())
    }

    @Test
    fun test_unpack_payload_failed() {
        val payload: Any = Any.pack(Int32Value.of(3))
        val exception: Exception = assertThrows(
            RuntimeException::class.java
        ) { RpcMapper.unpackPayload(payload, Status::class.java) }
        assertEquals(
            exception.message,
            "Type of the Any message does not match the given class. [com.google.rpc.Status]"
        )
    }

    @Test
    @DisplayName("test invalid payload that is not of type any")
    fun test_invalid_payload_that_is_not_type_any() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<Status> = RpcMapper.mapResponse(
            thatBarfsCrapyPayload.invokeMethod(buildTopic(), payload, buildUAttributes()), Status::class.java
        )
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(
            exception.message,
            "java.lang.RuntimeException: Protocol message contained an invalid tag (zero). [com.google.rpc" +
                    ".Status]"
        )
    }

    @Test
    @DisplayName("test invalid payload that is not of type any")
    fun test_invalid_payload_that_is_not_type_any_map_to_result() {
        val payload: UPayload = buildUPayload()
        val rpcResponse: CompletableFuture<RpcResult<Status>> = RpcMapper.mapResponseToResult(
            thatBarfsCrapyPayload.invokeMethod(buildTopic(), payload, buildUAttributes()), Status::class.java
        )
        assertTrue(rpcResponse.isCompletedExceptionally())
        val exception: Exception = assertThrows(ExecutionException::class.java, rpcResponse::get)
        assertEquals(
            exception.message,
            "java.lang.RuntimeException: Protocol message contained an invalid tag (zero). [com.google.rpc" +
                    ".Status]"
        )
    }

    @Test
    @Throws(InterruptedException::class)
    fun what_the_stub_looks_like() {
        val client: RpcClient = object : RpcClient {

            override fun invokeMethod(topic: UUri, payload: UPayload, attributes: UAttributes): CompletableFuture<UPayload> {
                return CompletableFuture.completedFuture(UPayload.empty())
            }
        }

        //Stub code
        val payload: UPayload = buildUPayload()
        val invokeMethodResponse: CompletableFuture<UPayload> = client.invokeMethod(
            buildTopic(), payload,
            buildUAttributes()
        )
        val stubReturnValue: CompletableFuture<CloudEvent> = rpcResponse(invokeMethodResponse)
        assertFalse(stubReturnValue.isCancelled())
    }

    companion object {
        private fun buildCloudEvent(): CloudEvent {
            return CloudEvent.newBuilder().setSpecVersion("1.0").setId("HARTLEY IS THE BEST")
                .setSource("http://example.com").build()
        }

        private fun buildUPayload(): UPayload {
            val any: Any = Any.pack(buildCloudEvent())
            return UPayload(any.toByteArray(), UPayloadFormat.UPAYLOAD_FORMAT_PROTOBUF)
        }

        private fun buildTopic(): UUri {
            return LongUriSerializer.instance().deserialize("//vcu.vin/hartley/1/rpc.Raise")
        }

        private fun buildUAttributes(): UAttributes {
            return UAttributesBuilder.request(
                UPriority.UPRIORITY_CS4,
                UUri.newBuilder().setEntity(UEntity.newBuilder().setName("hartley")).build(), 1000
            )
                .build()
        }

        private fun rpcResponse(
            invokeMethodResponse: CompletableFuture<UPayload>
        ): CompletableFuture<CloudEvent> {
            return invokeMethodResponse.handle { payload, exception ->
                val any: Any
                try {
                    any = Any.parseFrom(payload.data)
                } catch (e: InvalidProtocolBufferException) {
                    throw RuntimeException(e.message, e)
                }

                // invoke method had some unexpected problem.
                if (exception != null) {
                    throw RuntimeException(exception.message, exception)
                }

                // test to see if we have expected type
                if (any.`is`(CloudEvent::class.java)) {
                    try {
                        return@handle any.unpack(CloudEvent::class.java)
                    } catch (e: InvalidProtocolBufferException) {
                        throw RuntimeException(e.message, e)
                    }
                }

                // this will be called only if expected return type is not status, but status was returned to
                // indicate a problem.
                if (any.`is`(Status::class.java)) {
                    try {
                        val status: Status = any.unpack(Status::class.java)
                        throw RuntimeException(
                            String.format(
                                "Error returned, status code: [%s], message: [%s]",
                                Code.forNumber(status.code), status.message
                            )
                        )
                    } catch (e: InvalidProtocolBufferException) {
                        throw RuntimeException(
                            String.format("%s [%s]", e.message, "com.google.grpc.Status.class"), e
                        )
                    }
                }
                throw RuntimeException(String.format("Unknown payload type [%s]", any.typeUrl))
            }
        }
    }
}
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
 * SPDX-FileType: SOURCE
 * SPDX-FileCopyrightText: 2023 General Motors GTO LLC
 * SPDX-License-Identifier: Apache-2.0
 */

package org.eclipse.uprotocol.transport.builder

import org.eclipse.uprotocol.uuid.factory.UuidFactory
import org.eclipse.uprotocol.v1.*
import org.eclipse.uprotocol.v1.UUID
import java.util.*

/**
 * Builder for easy construction of the UAttributes object.
 */
class UAttributesBuilder
/**
 * Construct the UAttributesBuilder with the configurations that are required for every payload transport.
 *
 * @param id       Unique identifier for the message.
 * @param type     Message type such as Publish a state change, RPC request or RPC response.
 * @param priority uProtocol Prioritization classifications.
 */ private constructor(private val id: UUID, private val type: UMessageType, private val priority: UPriority) {
    private var ttl: Int? = null
    private var token: String? = null
    private var sink: UUri? = null
    private var plevel: Int? = null
    private var commstatus: Int? = null
    private var reqid: UUID? = null

    /**
     * Add the time to live in milliseconds.
     *
     * @param ttl the time to live in milliseconds.
     * @return Returns the UAttributesBuilder with the configured ttl.
     */
    fun withTtl(ttl: Int?): UAttributesBuilder {
        this.ttl = ttl
        return this
    }

    /**
     * Add the authorization token used for TAP.
     *
     * @param token the authorization token used for TAP.
     * @return Returns the UAttributesBuilder with the configured token.
     */
    fun withToken(token: String?): UAttributesBuilder {
        this.token = token
        return this
    }

    /**
     * Add the explicit destination URI.
     *
     * @param sink the explicit destination URI.
     * @return Returns the UAttributesBuilder with the configured sink.
     */
    fun withSink(sink: UUri?): UAttributesBuilder {
        this.sink = sink
        return this
    }

    /**
     * Add the permission level of the message.
     *
     * @param plevel the permission level of the message.
     * @return Returns the UAttributesBuilder with the configured plevel.
     */
    fun withPermissionLevel(plevel: Int?): UAttributesBuilder {
        this.plevel = plevel
        return this
    }

    /**
     * Add the communication status of the message.
     *
     * @param commstatus the communication status of the message.
     * @return Returns the UAttributesBuilder with the configured commstatus.
     */
    fun withCommStatus(commstatus: Int?): UAttributesBuilder {
        this.commstatus = commstatus
        return this
    }

    /**
     * Add the request ID.
     *
     * @param reqid the request ID.
     * @return Returns the UAttributesBuilder with the configured reqid.
     */
    fun withReqId(reqid: UUID?): UAttributesBuilder {
        this.reqid = reqid
        return this
    }

    /**
     * Construct the UAttributes from the builder.
     *
     * @return Returns a constructed
     */
    fun build(): UAttributes {
        val attributesBuilder = UAttributes.newBuilder().setId(id).setType(type).setPriority(priority)
        if (sink != null) {
            attributesBuilder.setSink(sink)
        }
        if (ttl != null) {
            attributesBuilder.setTtl(ttl!!)
        }
        if (plevel != null) {
            attributesBuilder.setPermissionLevel(plevel!!)
        }
        if (commstatus != null) {
            attributesBuilder.setCommstatus(commstatus!!)
        }
        if (reqid != null) {
            attributesBuilder.setReqid(reqid)
        }
        if (token != null) {
            attributesBuilder.setToken(token)
        }
        return attributesBuilder.build()
    }

    companion object {
        /**
         * Construct a UAttributesBuilder for a publish message.
         * @param priority The priority of the message.
         * @return Returns the UAttributesBuilder with the configured priority.
         */
        fun publish(priority: UPriority): UAttributesBuilder {
            return UAttributesBuilder(UuidFactory.Factories.UPROTOCOL.factory().create(),
                    UMessageType.UMESSAGE_TYPE_PUBLISH, priority)
        }

        /**
         * Construct a UAttributesBuilder for a notification message.
         * @param priority The priority of the message.
         * @param sink The destination URI.
         * @return Returns the UAttributesBuilder with the configured priority and sink.
         */
        fun notification(priority: UPriority, sink: UUri): UAttributesBuilder {
            return UAttributesBuilder(UuidFactory.Factories.UPROTOCOL.factory().create(),
                    UMessageType.UMESSAGE_TYPE_PUBLISH, priority).withSink(sink)
        }

        /**
         * Construct a UAttributesBuilder for a request message.
         * @param priority The priority of the message.
         * @param sink The destination URI.
         * @param ttl The time to live in milliseconds.
         * @return Returns the UAttributesBuilder with the configured priority, sink and ttl.
         */
        fun request(priority: UPriority, sink: UUri, ttl: Int): UAttributesBuilder {
            return UAttributesBuilder(UuidFactory.Factories.UPROTOCOL.factory().create(),
                    UMessageType.UMESSAGE_TYPE_REQUEST, priority).withTtl(ttl).withSink(sink)
        }

        /**
         * Construct a UAttributesBuilder for a response message.
         * @param priority The priority of the message.
         * @param sink The destination URI.
         * @param reqid The original request UUID used to correlate the response to the request.
         * @return Returns the UAttributesBuilder with the configured priority, sink and reqid.
         */
        fun response(priority: UPriority, sink: UUri, reqid: UUID): UAttributesBuilder {
            return UAttributesBuilder(UuidFactory.Factories.UPROTOCOL.factory().create(),
                    UMessageType.UMESSAGE_TYPE_RESPONSE, priority).withSink(sink).withReqId(reqid)
        }
    }
}
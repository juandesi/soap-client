/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws.api;

public class ServiceDefinition
{

    private final String baseEndpoint;
    private final String namespace;
    private final String serviceName;
    private final String portName;

    public ServiceDefinition(String baseEndpoint,
                             String namespace,
                             String serviceName,
                             String portName)
    {
        this.baseEndpoint = baseEndpoint.endsWith("/") ? baseEndpoint : baseEndpoint +"/";
        this.namespace = namespace.endsWith("/") ? namespace : namespace +"/";
        this.serviceName = serviceName;
        this.portName = portName;
    }

    public String getNamespace()
    {
        return namespace;
    }

    public String getServiceName()
    {
        return serviceName;
    }

    public String getPortName()
    {
        return portName;
    }

    public String getBaseEndpoint()
    {
        return baseEndpoint;
    }

}

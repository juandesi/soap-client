/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws;

import static javax.xml.ws.Endpoint.publish;
import static org.junit.Assert.assertTrue;
import org.mule.extension.ws.api.ServiceDefinition;
import org.mule.extension.ws.api.SoapProxyClient;
import org.mule.extension.ws.consumer.TestAttachments;

import javax.xml.ws.Endpoint;

import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

public class RunnerTEst
{

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static final String SERVICE_URL = "http://localhost:6043/attachmentService";
    private static final String NAMESPACE = "http://consumer.ws.extension.mule.org/";
    private static final ServiceDefinition TEST_SERVICE_DEFINITION = new ServiceDefinition(SERVICE_URL, NAMESPACE, "TestService", "TestPort");
    private static SoapProxyClient soapClient;


    public static void main(String[] args)
    {
        XMLUnit.setIgnoreWhitespace(true);
        Endpoint service = publish(SERVICE_URL, new TestAttachments());
        assertTrue(service.isPublished());
        soapClient = SoapProxyClient.create(TEST_SERVICE_DEFINITION);
    }
}

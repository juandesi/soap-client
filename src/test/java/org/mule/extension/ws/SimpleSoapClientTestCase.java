/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws;

import static javax.xml.ws.Endpoint.publish;
import static junit.framework.TestCase.assertNotNull;
import static org.custommonkey.xmlunit.XMLUnit.compareXML;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mule.extension.ws.SoapClientTestUtils.readXml;
import static org.mule.extension.ws.SoapClientTestUtils.xmlStreamToString;
import org.mule.extension.ws.api.ServiceDefinition;
import org.mule.extension.ws.api.SoapProxyClient;
import org.mule.extension.ws.consumer.TestService;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.ws.Endpoint;

import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class SimpleSoapClientTestCase
{

    private static final String SERVICE_URL = "http://localhost:6045/testService";
    private static final String NAMESPACE = "http://consumer.ws.extension.mule.org/";
    private static final ServiceDefinition TEST_SERVICE_DEFINITION = new ServiceDefinition(SERVICE_URL, NAMESPACE, "TestService", "TestPort");

    private Endpoint service;
    private SoapProxyClient soapClient;

    @Before
    public void setup()
    {
        XMLUnit.setIgnoreWhitespace(true);
        TestService testService = new TestService();
        service = publish(SERVICE_URL, testService);
        assertTrue(service.isPublished());
        soapClient = SoapProxyClient.create(TEST_SERVICE_DEFINITION);
    }

    @Test
    public void simpleOperation() throws Exception
    {
        XMLStreamReader output = soapClient.invoke("echo", readXml("request/echo.xml"));
        assertNotNull(output);
        assertSimilarXml(readXml("response/echo.xml"), output);
    }

    @Test
    public void complexTypeOperation() throws Exception
    {
        XMLStreamReader output = soapClient.invoke("echoAccount", readXml("request/echoAccount.xml"));
        assertNotNull(output);
        assertSimilarXml(readXml("response/echoAccount.xml"), output);
    }

    @Test
    public void noParamsOperation() throws Exception
    {
        XMLStreamReader output = soapClient.invoke("noParams", readXml("request/noParams.xml"));
        assertNotNull(output);
        assertSimilarXml(readXml("response/noParams.xml"), output);
    }

    // Add interceptor
    @Test
    public void failOperation() throws Exception
    {
        XMLStreamReader output = soapClient.invoke("fail", readXml("request/fail.xml"));
        assertNotNull(output);
        assertSimilarXml(readXml("response/fail.xml"), output);
    }

    @After
    public void tearDown()
    {
        service.stop();
    }

    private void assertSimilarXml(XMLStreamReader expected, XMLStreamReader output) throws Exception
    {
        String outputString = xmlStreamToString(output);
        String expectedString = xmlStreamToString(expected);
        System.out.println("Expected xml is: \n");
        System.out.println(prettyPrint(expectedString));
        System.out.println("\n\n\n Output is: \n");
        System.out.println(prettyPrint(outputString));
        Diff diff = compareXML(outputString, expectedString);
        assertThat(diff.similar(), is(true));
    }

    private String prettyPrint(String a) throws TransformerException, ParserConfigurationException, IOException, SAXException
    {
        DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        InputSource is = new InputSource();
        is.setCharacterStream(new StringReader(a));

        Document doc = db.parse(is);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        //initialize StreamResult with File object to save to file
        StreamResult result = new StreamResult(new StringWriter());

        DOMSource source = new DOMSource(doc);
        transformer.transform(source, result);
        return result.getWriter().toString();
    }
}

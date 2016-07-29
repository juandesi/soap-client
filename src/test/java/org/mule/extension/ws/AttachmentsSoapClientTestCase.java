/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static javax.xml.ws.Endpoint.publish;
import static org.custommonkey.xmlunit.XMLUnit.compareXML;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mule.extension.ws.SoapClientTestUtils.readXml;
import static org.mule.extension.ws.SoapClientTestUtils.xmlStreamToString;
import org.mule.extension.ws.api.Response;
import org.mule.extension.ws.api.ServiceDefinition;
import org.mule.extension.ws.api.SoapProxyClient;
import org.mule.extension.ws.consumer.TestAttachments;
import org.mule.runtime.core.util.IOUtils;

import java.io.IOException;
import java.io.InputStream;
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
import org.hamcrest.core.StringContains;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class AttachmentsSoapClientTestCase
{

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private static final String SERVICE_URL = "http://localhost:6043/attachmentService";
    private static final String NAMESPACE = "http://consumer.ws.extension.mule.org/";
    private static final ServiceDefinition TEST_SERVICE_DEFINITION = new ServiceDefinition(SERVICE_URL, NAMESPACE, "TestService", "TestPort");
    private static SoapProxyClient soapClient;

    @BeforeClass
    public static void setup()
    {
        XMLUnit.setIgnoreWhitespace(true);
        Endpoint service = publish(SERVICE_URL, new TestAttachments());
        assertTrue(service.isPublished());
        soapClient = SoapProxyClient.create(TEST_SERVICE_DEFINITION);
    }

    @Test
    public void upload() throws Exception
    {
        Response output = soapClient.invoke("uploadAttachment", readXml("request/attachment/uploadAttachment.xml"), emptyList(), singletonList(getAttachment()));
        assertSimilarXml(readXml("response/echoWithHeaders.xml"), output.getBody());
    }

    @Test
    public void download() throws Exception
    {
        Response response = soapClient.invoke("downloadAttachment", readXml("request/attachment/downloadAttachment.xml"));
        assertThat(response.getAtt().size(), is(1));
        assertThat(IOUtils.toString(response.getAtt().get(0)), StringContains.containsString("some content"));
    }

    private InputStream getAttachment()
    {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream("attachment_file.txt");
    }

    private void assertSimilarXml(XMLStreamReader expected, XMLStreamReader output) throws Exception
    {
        String outputString = xmlStreamToString(output);
        String expectedString = xmlStreamToString(expected);
        Diff diff = compareXML(outputString, expectedString);

        if (!diff.similar())
        {
            System.out.println("Expected xml is: \n");
            System.out.println(prettyPrint(expectedString));
            System.out.println("\n\n\n Output is: \n");
            System.out.println(prettyPrint(outputString));
        }

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

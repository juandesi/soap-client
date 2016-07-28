/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws;

import static javax.xml.stream.XMLInputFactory.newInstance;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.apache.cxf.staxutils.StaxSource;

public class SoapClientTestUtils
{
    private static XMLInputFactory factory = newInstance();

    public static XMLStreamReader readXml(final String filename) throws XMLStreamException
    {
        final InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename);
        final Reader myReader = new InputStreamReader(is);
        return factory.createXMLStreamReader(myReader);
    }

    public static String xmlStreamToString(XMLStreamReader xmlStreamReader) throws TransformerException
    {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter stringWriter = new StringWriter();
        final StaxSource xmlSource = new StaxSource(xmlStreamReader);
        transformer.transform(xmlSource, new StreamResult(stringWriter));

        return stringWriter.toString();
    }
}

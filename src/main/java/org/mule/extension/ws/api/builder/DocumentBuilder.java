/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws.api.builder;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.cxf.staxutils.StaxUtils;
import org.w3c.dom.Document;

public class DocumentBuilder
{

    private String type;
    private String xmlElementType;

    public String getType()
    {
        return type;
    }

    public String getXmlElementType()
    {
        return xmlElementType;
    }

    protected DocumentBuilder(String type, String xmlElementType)
    {
        this.type = type;
        this.xmlElementType = xmlElementType;
    }

    public static DocumentBuilder getInstance()
    {
        return new DocumentBuilder(null, null);
    }

    public static DocumentBuilder getInstance(String type, String xmlElementType)
    {
        return new DocumentBuilder(type, xmlElementType);
    }

    public Document createDocument(XMLStreamReader payload) throws ParserConfigurationException, XMLStreamException
    {
        return StaxUtils.read(payload);
    }

}

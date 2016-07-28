/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws.api.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;

public class HeadersBuilder
{

    private final String namespace;
    private Map<String, String> headers = new HashMap<>();
    private List<SOAPElement> elements = new ArrayList<>();

    public static HeadersBuilder getInstance(String namespace)
    {
        return new HeadersBuilder(namespace);
    }

    private HeadersBuilder(String namespace)
    {
        this.namespace = namespace;
    }

    public HeadersBuilder addHeader(String key, String value)
    {
        headers.put(key, value);
        return this;
    }

    public HeadersBuilder addHeaders(Map<String, String> map)
    {
        headers.putAll(map);
        return this;
    }

    public HeadersBuilder addHeader(SOAPElement element)
    {
        elements.add(element);
        return this;
    }

    public SOAPHeader build(SOAPHeader header)
    {
        headers.entrySet().forEach(h ->
                                   {
                                       try
                                       {
                                           QName qname = new QName(namespace, h.getKey(), "SOAP_");
                                           SOAPElement soapElement = header.addChildElement(qname);
                                           soapElement.addTextNode(h.getValue());
                                       }
                                       catch (SOAPException e)
                                       {
                                           throw new RuntimeException(e);
                                       }
                                   });

        elements.forEach(h ->
                        {
                            try
                            {
                                header.addChildElement(h);
                            }
                            catch (SOAPException e)
                            {
                                throw new RuntimeException(e);
                            }
                        });

        return header;
    }
}

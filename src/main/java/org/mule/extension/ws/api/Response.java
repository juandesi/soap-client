/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws.api;

import java.io.InputStream;
import java.util.List;

import javax.xml.stream.XMLStreamReader;

public class Response
{
    private XMLStreamReader body;

    private List<InputStream> att;

    public Response(XMLStreamReader body, List<InputStream> att)
    {
        this.body = body;
        this.att = att;
    }

    public XMLStreamReader getBody()
    {
        return body;
    }

    public List<InputStream> getAtt()
    {
        return att;
    }
}

/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws.api;


import static java.util.Collections.emptyList;
import org.mule.extension.ws.api.builder.DocumentBuilder;
import org.mule.extension.ws.api.exception.SoapFaultException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

public class SoapProxyClient
{

    private static final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
    private static final String XMLSOAP_ORG_SOAP_ENCODING_NAMESPACE = "http://schemas.xmlsoap.org/soap/encoding/";

    private final ServiceDefinition serviceDefinition;
    private final MessageFactory messageFactory;

    private SoapProxyClient(ServiceDefinition service, SoapVersion version)
    {
        serviceDefinition = service;
        try
        {
            messageFactory = MessageFactory.newInstance(version.getProtocol());
        }
        catch (SOAPException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static SoapProxyClient create(ServiceDefinition service)
    {
        return new SoapProxyClient(service, SoapVersion.SOAP_11);
    }

    public XMLStreamReader invoke(String operationName, XMLStreamReader payload)
    {
        return invoke(operationName, payload, DocumentBuilder.getInstance(), emptyList());
    }

    public XMLStreamReader invoke(String operationName, XMLStreamReader payload, List<XMLStreamReader> headers)
    {
        return invoke(operationName, payload, DocumentBuilder.getInstance(), headers);
    }

    public XMLStreamReader invoke(String operationName, XMLStreamReader payload, DocumentBuilder docBuilder, List<XMLStreamReader> headers)
    {

        try
        {
            final SOAPMessage soapRequest = buildSoapRequest(payload, operationName, docBuilder, headers);

            // Aca deberia ir la cagada que llama a lo que se configuro como transporte
            InputStream post = post(soapRequest);
            // ***********

            SOAPMessage message = messageFactory.createMessage(null, post);
            SOAPBody resultBody = message.getSOAPBody();

            if (resultBody.hasFault())
            {
                SOAPFault fault = resultBody.getFault();
                throw new SoapFaultException(fault.getFaultCodeAsQName(),
                                             null,
                                             fault.getFaultString(),
                                             fault.getDetail());
            }

            Document document = resultBody.extractContentAsDocument();
            return inputFactory.createXMLStreamReader(new DOMSource(document));

        }
        catch (SoapFaultException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }


    // Just to hand test. REMOVE
    private String soapMessageToString(SOAPMessage message) throws SOAPException, IOException
    {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        message.writeTo(os);
        return new String(os.toByteArray());
    }

    private InputStream post(SOAPMessage message) throws TransformerException, SOAPException, XMLStreamException, IOException
    {
        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(serviceDefinition.getBaseEndpoint());

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        message.writeTo(os);

        HttpEntity entity = new ByteArrayEntity(os.toByteArray());
        post.setEntity(entity);
        HttpResponse response = client.execute(post);
        return response.getEntity().getContent();
    }

    public SOAPMessage buildSoapRequest(XMLStreamReader payload,
                                        String operationName,
                                        DocumentBuilder docBuilder,
                                        List<XMLStreamReader> headers) throws Exception
    {
        SOAPMessage result = messageFactory.createMessage();
        SOAPPart part = result.getSOAPPart();
        SOAPEnvelope envelope = part.getEnvelope();

        // ???????
        envelope.setEncodingStyle(XMLSOAP_ORG_SOAP_ENCODING_NAMESPACE);

        String namespace = serviceDefinition.getNamespace();
        envelope.addNamespaceDeclaration("body", namespace + operationName + "/");
        envelope.addNamespaceDeclaration("header", namespace);

        SOAPHeader soapHeader = envelope.getHeader();

        // Request Header
        //if (soapHeaderBuilder != null) {
        //    soapHeaderBuilder.build(header, serviceDefinition);
        //}

        for(XMLStreamReader reader : headers)
        {
            Document document = docBuilder.createDocument(reader);
            Element element = document.getDocumentElement();
            soapHeader.addChildElement(new QName(namespace, element.getTagName())).addTextNode(element.getTextContent());
        }

        //XMLStreamReader callsPayload = payload;
        //// Compose the soap:Body payload
        //if (callsPayload == null) {
        //    String soapMethodsCallNamespace = namespace.endsWith("/") ? namespace.substring(0, namespace.length() - 1) : namespace;
        //    callsPayload = XmlConverterUtils.computeCallsPayloadForMethodWithNoParameter(operationName, soapMethodsCallNamespace);
        //}

        /*
         * We created an enhancement request(DEVKIT-2182) in order for the xsi:type attribute to be added at dataSense. Until then, we will use the ComplexDocumentBuilder to add it
         * at runtime
         */

        Document document = docBuilder.createDocument(payload);
        SOAPBody body = envelope.getBody();
        body.addDocument(document);
        result.saveChanges();

        return result;
    }


    static XMLStreamReader soapResponseToXmlStream(SOAPMessage soapResponse)
            throws SOAPException, XMLStreamException
    {

        final SOAPBodyElement sourceContent = (SOAPBodyElement) soapResponse.getSOAPBody().getChildElements().next();

        // Add the namespaces from the response Envelope tag to the result
        // tag - so it can be parsed successfully by DataMapper
        final SOAPPart soapPart = soapResponse.getSOAPPart();
        final SOAPEnvelope soapEnvelope = soapPart.getEnvelope();

        final SOAPPart responsePart = soapResponse.getSOAPPart();
        final NodeList childNodes = responsePart.getChildNodes();

        final NamedNodeMap envelopeAttributes = childNodes.item(0).getAttributes();
        for (int i = 0; i < envelopeAttributes.getLength(); i++)
        {
            final org.w3c.dom.Node node = envelopeAttributes.item(i);
            final String nodeName = node.getNodeName();
            final Name name = soapEnvelope.createName(nodeName);
            sourceContent.addAttribute(name, node.getNodeValue());
        }

        // Build XML InputStream
        final DOMSource source = new DOMSource(sourceContent);
        final XMLStreamReader reader = inputFactory.createXMLStreamReader(source);
        final StAXSource stAXSource = new StAXSource(reader);
        return stAXSource.getXMLStreamReader();
    }

}

/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws.api;


import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import org.mule.extension.ws.api.builder.DocumentBuilder;
import org.mule.extension.ws.api.exception.SoapFaultException;
import org.mule.runtime.core.util.IOUtils;

import java.io.ByteArrayInputStream;
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
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
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

    public Response invoke(String operationName, XMLStreamReader payload)
    {
        return invoke(operationName, payload, emptyList(), emptyList());
    }

    public Response invoke(String operationName, XMLStreamReader payload, List<XMLStreamReader> headers, List<InputStream> attachments)
    {
        try
        {
            final SOAPMessage soapRequest = buildSoapRequest(payload, operationName, DocumentBuilder.getInstance(), headers);

            // Aca deberia ir la cagada que llama a lo que se configuro como transporte

            // La request tiene que armarse como un "Multipart Payload" con Attachments (content, contentType, encoding) y el Body
            // y pasarselo al transporte especifico que se configuro para que el sepa que hacer especificamente con eso.
            SoapResponse post = post(soapRequest, attachments, operationName);

            SOAPMessage message = messageFactory.createMessage(null, post.getBody());
            SOAPBody resultBody = message.getSOAPBody();

            if (resultBody.hasFault() && resultBody.getFault() != null) // check this if
            {
                SOAPFault fault = resultBody.getFault();
                throw new SoapFaultException(fault.getFaultCodeAsQName(),
                                             null, // This parameter only apply for SOAP 1.2
                                             fault.getFaultString(),
                                             fault.getDetail());
            }

            Document document = resultBody.extractContentAsDocument();
            return new Response(inputFactory.createXMLStreamReader(new DOMSource(document)), post.getAttachments());

        }
        catch (SoapFaultException e)
        {
            // Error on the SOAPCall
            throw e;
        }
        catch (Exception e)
        {
            // What should I throw here???? ni idea wachin, soap fault maybe?
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


    // SUPER IGNORE este metodo es momentaneo para la POC, para transportar el mensaje por algun medio
    private SoapResponse post(SOAPMessage message, List<InputStream> attachments, String operationName) throws TransformerException, SOAPException, XMLStreamException, IOException
    {

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        message.writeTo(os);

        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(serviceDefinition.getBaseEndpoint());
        HttpEntity entity;
        if (!attachments.isEmpty())
        {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addPart(FormBodyPartBuilder.create("envelope", new StringBody(new String(os.toByteArray()), ContentType.TEXT_XML)).build());

            for (InputStream attachment : attachments)
            {
                StringBody part = new StringBody(org.apache.commons.io.IOUtils.toString(attachment), ContentType.TEXT_PLAIN);
                builder.addPart(FormBodyPartBuilder.create("9999", part).addField("Content-ID", "<9999>").addField("Part", "9999").addField("Type", "CONTENT").build());
            }

            builder.seContentType(ContentType.create("multipart/related"));
            entity = builder.build();
            ByteArrayOutputStream test = new ByteArrayOutputStream();
            entity.writeTo(test);

            System.out.println(new String(test.toByteArray()));
        }
        else
        {
            entity = EntityBuilder.create().setText(new String(os.toByteArray())).build();
        }

        httpPost.setEntity(entity);
        CloseableHttpResponse response = client.execute(httpPost);

        response.close();

        client.close();

        httpPost.addHeader("SOAPAction", operationName);  // ????? fijate esto pero por ahora lo mando igual.

        HttpEntity result = response.getEntity();

        if (result.getContentType().getValue().contains("multipart"))
        {
            // Magia negra para obtener body y attachment del test. esto vuela con el transporte configurado
            String s = IOUtils.toString(result.getContent());
            String[] split = s.split("\r\n\r\n");

            List<InputStream> streams = split.length > 2 ? singletonList(new ByteArrayInputStream(split[2].substring(0, split[2].indexOf("--uuid")).getBytes())) : emptyList();

            String is = split[1].contains("--uuid") ? split[1].substring(0, split[1].indexOf("--uuid")) : split[1];

            return new SoapResponse(new ByteArrayInputStream(is.getBytes()), streams);
        }
        else
        {
            return new SoapResponse(result.getContent(), emptyList());
        }

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

        for (XMLStreamReader reader : headers)
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


    private class SoapResponse
    {
        private InputStream body;
        private List<InputStream> attachments;

        public SoapResponse(InputStream body, List<InputStream> attachments)
        {
            this.body = body;
            this.attachments = attachments;
        }

        public InputStream getBody()
        {
            return body;
        }

        public List<InputStream> getAttachments()
        {
            return attachments;
        }
    }
}

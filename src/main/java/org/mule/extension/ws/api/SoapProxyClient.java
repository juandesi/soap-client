/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extension.ws.api;


import static java.util.Collections.emptyMap;
import org.mule.extension.ws.api.builder.DocumentBuilder;

import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPBodyElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

public class SoapProxyClient
{

    private static XMLInputFactory inputFactory = XMLInputFactory.newInstance();
    private static final String XMLSOAP_ORG_SOAP_ENCODING_NAMESPACE = "http://schemas.xmlsoap.org/soap/encoding/";

    private final ServiceDefinition serviceDefinition;

    private SoapProxyClient(ServiceDefinition service)
    {
        serviceDefinition = service;
    }

    public static SoapProxyClient create(ServiceDefinition service)
    {
        return new SoapProxyClient(service);
    }

    public XMLStreamReader invoke(String operationName, XMLStreamReader payload)
    {
        return invoke(operationName, payload, DocumentBuilder.getInstance(), emptyMap());
    }

    public XMLStreamReader invoke(String operationName, XMLStreamReader payload, Map<String, String> headers)
    {
        return invoke(operationName, payload, DocumentBuilder.getInstance(), headers);
    }

    //private InputStream post(SOAPMessage message) throws TransformerException, SOAPException, XMLStreamException, IOException
    //{
    //    HttpClient client = new DefaultHttpClient();
    //    HttpPost post = new HttpPost(definition.getBaseEndpoint());
    //    XMLStreamReader xmlStreamReader = soapMessageToXmlStream(message);
    //
    //    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    //    StringWriter stringWriter = new StringWriter();
    //    transformer.transform(new StAXSource(xmlStreamReader), new StreamResult(stringWriter));
    //
    //    HttpEntity entity = new ByteArrayEntity(stringWriter.toString().getBytes());
    //    post.setEntity(entity);
    //    HttpResponse response = client.execute(post);
    //    return response.getEntity().getContent();
    //}

    public XMLStreamReader invoke(String operationName, XMLStreamReader payload, DocumentBuilder docBuilder, Map<String, String> headers){

        try {
            final Dispatch<SOAPMessage> dispatch = buildMessageDispatch(operationName);

            final SOAPMessage soapRequest = buildSoapRequest(payload, operationName, dispatch, docBuilder, headers);

            final SOAPMessage soapResponse = dispatch.invoke(soapRequest);






            // If the serviceDefinition responds a 202 status, the payload will be null.
            // Therefore soapResponse is null. See org.apache.cxf.endpoint.ClientImpl,line 609.
            if (soapResponse != null && !soapResponse.getSOAPBody().hasFault())  {
                final SOAPBodyElement sourceContent = (SOAPBodyElement) soapResponse.getSOAPBody().getChildElements().next();

                // Add the namespaces from the response Envelope tag to the result
                // tag - so it can be parsed successfully by DataMapper
                final SOAPPart soapPart = soapRequest.getSOAPPart();
                final SOAPEnvelope soapEnvelope = soapPart.getEnvelope();

                return soapResponseToXmlStream(soapResponse, sourceContent, soapEnvelope);
            }



        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    public Dispatch<SOAPMessage> buildMessageDispatch(String operationName) {

        // QNames for serviceDefinition as defined in wsdl.
        final String namespace = serviceDefinition.getNamespace();
        final String operation = serviceDefinition.getServiceName();
        final QName serviceQName = new QName(namespace, operation);

        // QName for Port As defined in wsdl.
        final String portName = serviceDefinition.getPortName();
        final QName portQName = new QName(namespace, portName);

        // Endpoint Address
        final String baseEndpoint = serviceDefinition.getBaseEndpoint();

        // Create a dynamic Service instance
        final Service service = Service.create(serviceQName);

        // Add a port to the Service
        service.addPort(portQName, SOAPBinding.SOAP11HTTP_BINDING, baseEndpoint);

        // Create a dispatch instance
        final Dispatch<SOAPMessage> dispatch = service.createDispatch(portQName, SOAPMessage.class, Service.Mode.MESSAGE);

        // Optionally Configure RequestContext to send SOAPAction HTTP Header
        final Map<String, Object> rc = dispatch.getRequestContext();
        rc.put(BindingProvider.SOAPACTION_USE_PROPERTY, Boolean.TRUE);
        rc.put(BindingProvider.SOAPACTION_URI_PROPERTY, operationName);

        return dispatch;
    }

    /**
     * Build SOAP Request based on the provided parameters
     *
     * @param payload       Full payload to be sent without the operation
     * @param operationName Operation name to be invoked.
     * @param dispatch      Dispatch object used for the invocation.
     * @param docBuilder    class used to transform the reader into a Document and, if necessary, enrich it with an xsi:type attribute
     * @return SOAPMessage ready to be sent.
     * @throws SOAPException                when it cannot construct the SOAPMessage
     * @throws XMLStreamException           when it cannot successfully create the XMLStreamReader
     * @throws ParserConfigurationException when it cannot properly modify the payload with the xsi:type attribute
     */
    public SOAPMessage buildSoapRequest(XMLStreamReader payload, String operationName, Dispatch<SOAPMessage> dispatch,
                                        DocumentBuilder docBuilder, Map<String, String> headers) throws Exception{

        // Obtain a pre-configured SAAJ MessageFactory
        final SOAPBinding binding = (SOAPBinding) dispatch.getBinding();
        final MessageFactory msgFactory = binding.getMessageFactory();

        // Create SOAPMessage Request
        final SOAPMessage result = msgFactory.createMessage();

        final SOAPPart part = result.getSOAPPart();

        // Gets the elements SOAPEnvelope, header and body.
        final SOAPEnvelope env = part.getEnvelope();
        env.setEncodingStyle(XMLSOAP_ORG_SOAP_ENCODING_NAMESPACE);

        final String namespace = serviceDefinition.getNamespace();
        env.addNamespaceDeclaration("body", namespace + operationName + "/");
        env.addNamespaceDeclaration("header", namespace);

        // Request Header
        //final SOAPHeader header = env.getHeader();
        //if (soapHeaderBuilder != null) {
        //    soapHeaderBuilder.build(header, serviceDefinition);
        //}

        //final Map<String, String> params = getHeaderParams();
        //final String headerPrefix = serviceDefinition.getHeaderPrefix();
        //for (final String key : params.keySet()) {
        //    final QName qname = new QName(namespace, key, headerPrefix);
        //    final SOAPElement soapElement = header.addChildElement(qname);
        //    final String headerName = params.get(key);
        //    soapElement.addTextNode(headerName);
        //}

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
        final Document document = docBuilder.createDocument(payload);
        final SOAPBody body = env.getBody();
        body.addDocument(document);
        result.saveChanges();

        return result;
    }


    static XMLStreamReader soapResponseToXmlStream(SOAPMessage soapResponse, SOAPBodyElement sourceContent, SOAPEnvelope soapEnvelope)
            throws SOAPException, XMLStreamException {

        final SOAPPart responsePart = soapResponse.getSOAPPart();
        final NodeList childNodes = responsePart.getChildNodes();

        final NamedNodeMap envelopeAttributes = childNodes.item(0).getAttributes();
        for (int i = 0; i < envelopeAttributes.getLength(); i++) {
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

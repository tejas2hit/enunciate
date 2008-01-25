/*
 * Copyright 2006 Web Cohesion
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.enunciate.modules.rest;

import org.codehaus.enunciate.rest.annotations.VerbType;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContextException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.AbstractController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.activation.DataHandler;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An xml exporter for a REST resource.
 *
 * @author Ryan Heaton
 */
public class RESTResourceXMLExporter extends AbstractController {

  private final DocumentBuilder documentBuilder;
  private final RESTResource resource;
  private HandlerExceptionResolver exceptionHandler = new RESTExceptionHandler();
  private Map<String, String> ns2prefix;
  private String[] supportedMethods;

  public RESTResourceXMLExporter(RESTResource resource) {
    DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
    builderFactory.setNamespaceAware(false);
    try {
      documentBuilder = builderFactory.newDocumentBuilder();
    }
    catch (ParserConfigurationException e) {
      throw new RuntimeException(e);
    }
    this.resource = resource;
  }

  @Override
  protected void initApplicationContext() throws BeansException {
    super.initApplicationContext();

    if (resource == null) {
      throw new ApplicationContextException("A REST resource must be provided.");
    }

    Set<VerbType> supportedVerbs = resource.getSupportedVerbs();
    String[] supportedMethods = new String[supportedVerbs.size()];
    int i = 0;
    for (VerbType supportedVerb : supportedVerbs) {
      String method;
      switch (supportedVerb) {
        case create:
          method = "PUT";
          break;
        case read:
          method = "GET";
          break;
        case update:
          method = "POST";
          break;
        case delete:
          method = "DELETE";
          break;
        default:
          throw new IllegalStateException("Unsupported verb: " + supportedVerb);
      }
      supportedMethods[i++] = method;
    }
    this.supportedMethods = supportedMethods;
    super.setSupportedMethods(new String[] { "GET", "PUT", "POST", "DELETE" });
  }

  protected ModelAndView handleRequestInternal(HttpServletRequest request, HttpServletResponse response) throws Exception {
    String httpMethod = request.getHeader("X-HTTP-Method-Override");
    if ((httpMethod == null) || ("".equals(httpMethod.trim()))) {
      httpMethod = request.getMethod().toUpperCase();
    }
    else {
      httpMethod = httpMethod.toUpperCase();
    }

    VerbType verb;
    if ("PUT".equals(httpMethod)) {
      verb = VerbType.create;
    }
    else if ("GET".equals(httpMethod)) {
      verb = VerbType.read;
    }
    else if ("POST".equals(httpMethod)) {
      verb = VerbType.update;
    }
    else if ("DELETE".equals(httpMethod)) {
      verb = VerbType.delete;
    }
    else {
      throw new MethodNotAllowedException(this.supportedMethods);
    }

    if (!resource.getSupportedVerbs().contains(verb)) {
      throw new MethodNotAllowedException(this.supportedMethods);
    }

    try {
      return handleRESTOperation(verb, request, response);
    }
    catch (Exception e) {
      if (this.exceptionHandler != null) {
        return this.exceptionHandler.resolveException(request, response, this, e);
      }
      else {
        throw e;
      }
    }
  }

  /**
   * Handles a specific REST operation.
   *
   * @param verb The verb.
   * @param request The request.
   * @param response The response.
   * @return The model and view.
   */
  protected ModelAndView handleRESTOperation(VerbType verb, HttpServletRequest request, HttpServletResponse response) throws Exception {
    RESTOperation operation = resource.getOperation(verb);
    if (!isOperationAllowed(operation)) {
      response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Unsupported verb: " + verb);
      return null;
    }

    Document document = documentBuilder.newDocument();

    Unmarshaller unmarshaller = operation.getSerializationContext().createUnmarshaller();
    unmarshaller.setAttachmentUnmarshaller(RESTAttachmentUnmarshaller.INSTANCE);

    String requestContext = request.getRequestURI().substring(request.getContextPath().length());
    Map<String, String> contextParameters;
    try {
      contextParameters = resource.getContextParameterAndProperNounValues(requestContext);
    }
    catch (IllegalArgumentException e) {
      response.sendError(HttpServletResponse.SC_NOT_FOUND, request.getRequestURI());
      return null;
    }

    Object properNounValue = null;
    HashMap<String, Object> contextParameterValues = new HashMap<String, Object>();
    for (Map.Entry<String, String> entry : contextParameters.entrySet()) {
      if (entry.getKey() == null) {
        if (operation.getProperNounType() != null) {
          if (!String.class.isAssignableFrom(operation.getProperNounType())) {
            Element element = document.createElement("unimportant");
            element.appendChild(document.createTextNode(contextParameters.get(entry.getKey())));
            properNounValue = unmarshaller.unmarshal(element, operation.getProperNounType()).getValue();
          }
          else {
            properNounValue = contextParameters.get(entry.getKey());
          }
        }
      }
      else {
        Class contextParameterType = operation.getContextParameterTypes().get(entry.getKey());
        if (contextParameterType != null) {
          if (!String.class.isAssignableFrom(contextParameterType)) {
            Element element = document.createElement("unimportant");
            element.appendChild(document.createTextNode(contextParameters.get(entry.getKey())));
            contextParameterValues.put(entry.getKey(), unmarshaller.unmarshal(element, contextParameterType).getValue());
          }
          else {
            contextParameterValues.put(entry.getKey(), contextParameters.get(entry.getKey()));
          }
        }
      }
    }

    if ((properNounValue == null) && (operation.isProperNounOptional() != null) && (!operation.isProperNounOptional())) {
      throw new MissingParameterException("A specific '" + resource.getNoun() + "' must be specified on the URL.");
    }

    HashMap<String, Object> adjectives = new HashMap<String, Object>();
    for (String adjective : operation.getAdjectiveTypes().keySet()) {
      Object adjectiveValue = null;

      String[] parameterValues = request.getParameterValues(adjective);
      if ((parameterValues != null) && (parameterValues.length > 0)) {
        Class adjectiveType = operation.getAdjectiveTypes().get(adjective);
        Class componentType = adjectiveType;
        if (adjectiveType.isArray()) {
          componentType = adjectiveType.getComponentType();
        }
        Object adjectiveValues = Array.newInstance(componentType, parameterValues.length);

        for (int i = 0; i < parameterValues.length; i++) {
          if (!String.class.isAssignableFrom(componentType)) {
            Element element = document.createElement("unimportant");
            element.appendChild(document.createTextNode(parameterValues[i]));
            Array.set(adjectiveValues, i, unmarshaller.unmarshal(element, componentType).getValue());
          }
          else {
            Array.set(adjectiveValues, i, parameterValues[i]);
          }
        }

        if (adjectiveType.isArray()) {
          adjectiveValue = adjectiveValues;
        }
        else {
          adjectiveValue = Array.get(adjectiveValues, 0);
        }
      }

      if ((adjectiveValue == null) && (!operation.getAdjectivesOptional().get(adjective))) {
        throw new MissingParameterException("Missing request parameter: " + adjective);
      }

      adjectives.put(adjective, adjectiveValue);
    }

    Object nounValue = null;
    if (operation.getNounValueType() != null) {
      try {
        //if the operation has a noun value type, unmarshall it from the body....
        nounValue = unmarshaller.unmarshal(request.getInputStream());
      }
      catch (Exception e) {
        //if we can't unmarshal the noun value, continue if the noun value is optional.
        if (!operation.isNounValueOptional()) {
          throw e;
        }
      }
    }

    Object result = operation.invoke(properNounValue, contextParameterValues, adjectives, nounValue);
    return new ModelAndView(createView(operation, result));
  }

  /**
   * Create the REST view for the specified operation and result.
   *
   * @param operation The operation.
   * @param result The result.
   * @return The view.
   */
  protected View createView(RESTOperation operation, Object result) {
    if (result instanceof DataHandler) {
      return new DataHandlerView((DataHandler) result);
    }
    else if (operation.isWrapsPayload()) {
      return new RESTPayloadView(operation, result);
    }
    else {
      return createRESTView(operation, result);
    }
  }

  /**
   * Create the REST view for the specified operation and result.
   *
   * @param operation The operation.
   * @param result The result.
   * @return The view.
   */
  protected RESTResultView createRESTView(RESTOperation operation, Object result) {
    return new RESTResultView(operation, result, getNamespaces2Prefixes());
  }

  /**
   * Whether the specified operation is allowed.
   *
   * @param operation The operation to test whether it is allowed.
   * @return Whether the specified operation is allowed.
   */
  protected boolean isOperationAllowed(RESTOperation operation) {
    return operation != null;
  }

  /**
   * The map of namespaces to prefixes.
   *
   * @param ns2prefix The map of namespaces to prefixes.
   */
  public void setNamespaces2Prefixes(Map<String, String> ns2prefix) {
    this.ns2prefix = ns2prefix;
  }

  /**
   * The map of namespaces to prefixes.
   *
   * @return The map of namespaces to prefixes.
   */
  public Map<String, String> getNamespaces2Prefixes() {
    return this.ns2prefix;
  }
}
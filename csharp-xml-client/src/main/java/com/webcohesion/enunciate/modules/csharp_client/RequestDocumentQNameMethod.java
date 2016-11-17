/**
 * Copyright © 2006-2016 Web Cohesion (info@webcohesion.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webcohesion.enunciate.modules.csharp_client;

import com.webcohesion.enunciate.modules.jaxws.model.WebMethod;
import com.webcohesion.enunciate.modules.jaxws.model.WebParam;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateModelException;

import javax.jws.soap.SOAPBinding;
import javax.xml.namespace.QName;
import java.util.Collection;
import java.util.List;

/**
 * Gets the QName of the request document for a given method.
 *
 * @author Ryan Heaton
 */
public class RequestDocumentQNameMethod implements TemplateMethodModelEx {

  /**
   * Gets the client-side package for the type, type declaration, package, or their string values.
   *
   * @param list The arguments.
   * @return The string value of the client-side package.
   */
  public Object exec(List list) throws TemplateModelException {
    if (list.size() < 1) {
      throw new TemplateModelException("The requestQName method method must have a web method as a parameter.");
    }

    TemplateModel from = (TemplateModel) list.get(0);
    Object unwrapped = new BeansWrapperBuilder(Configuration.getVersion()).build().unwrap(from);
    if (!(unwrapped instanceof WebMethod)) {
      throw new TemplateModelException("A web method must be provided.");
    }

    WebMethod webMethod = (WebMethod) unwrapped;
    if (webMethod.getSoapBindingStyle() != SOAPBinding.Style.DOCUMENT || webMethod.getSoapUse() != SOAPBinding.Use.LITERAL) {
      throw new TemplateModelException("No request document qname available for a " + webMethod.getSoapBindingStyle() + "/" + webMethod.getSoapUse() + " web method.");
    }
    if (webMethod.getRequestWrapper() != null) {
      return new QName(webMethod.getRequestWrapper().getElementNamespace(), webMethod.getRequestWrapper().getElementName());
    }
    else if (webMethod.getSoapParameterStyle() == SOAPBinding.ParameterStyle.BARE) {
      Collection<WebParam> params = webMethod.getWebParameters();
      for (WebParam param : params) {
        if (!param.isHeader()) {
          return new QName(webMethod.getDeclaringEndpointInterface().getTargetNamespace(), param.getElementName());
        }
      }
    }

    return null;
  }

}
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sparrow.mvc.mapping.impl;

import com.sparrow.constant.CONFIG;
import com.sparrow.constant.CONSTANT;
import com.sparrow.constant.EXTENSION;
import com.sparrow.constant.magic.SYMBOL;
import com.sparrow.container.Container;
import com.sparrow.core.Pair;
import com.sparrow.core.spi.ApplicationContext;
import com.sparrow.mvc.ServletInvocableHandlerMethod;
import com.sparrow.mvc.RequestParameters;
import com.sparrow.mvc.mapping.HandlerMapping;
import com.sparrow.support.web.ServletUtility;
import com.sparrow.utility.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author harry
 */
public class UrlMethodHandlerMapping implements HandlerMapping {

    private static Logger logger = LoggerFactory.getLogger(UrlMethodHandlerMapping.class);

    private ServletUtility servletUtility = ServletUtility.getInstance();

    private Map<String, ServletInvocableHandlerMethod> mapping = new HashMap<String, ServletInvocableHandlerMethod>();

    private Map<String, ServletInvocableHandlerMethod> dynamicMapping = new HashMap<String, ServletInvocableHandlerMethod>();

    public UrlMethodHandlerMapping() {
        this.init();
    }

    private void init() {
        Container container = ApplicationContext.getContainer();
        String xmlConfig = Config.getValue(CONFIG.MVC_CONFIG);
        if (StringUtility.isNullOrEmpty(xmlConfig)) {
            xmlConfig = "/controller.xml";
        }
        List<Element> actionElementList;
        try {
            actionElementList = Xml.getElementsByTagName(Xml.getXmlDocumentByPath(xmlConfig, "mvc.dtd"),
                    "action");
            for (Element actionElement : actionElementList) {
                ServletInvocableHandlerMethod invocableHandlerMethod = new ServletInvocableHandlerMethod();
                String actionName = actionElement.getAttribute("name");
                String beanName = ((Element) actionElement.getParentNode())
                        .getAttribute("name");

                invocableHandlerMethod.setActionName(actionName);
                invocableHandlerMethod.setJson(actionName.endsWith(EXTENSION.JSON));
                invocableHandlerMethod.setValidatePrivilege(Boolean.valueOf(actionElement
                        .getAttribute("validatePrivilege")));
                String loginType = actionElement.getAttribute("login");
                String validateRequest = actionElement.getAttribute("validateRequest");
                if (StringUtility.isNullOrEmpty(validateRequest)) {
                    validateRequest = "true";
                }
                int intLoginType = StringUtility.isNullOrEmpty(loginType) ? 0
                        : Integer.valueOf(loginType);
                invocableHandlerMethod.setValidateRequest(Boolean.valueOf(validateRequest));
                invocableHandlerMethod.setLoginType(intLoginType);
                String actionMethodName = actionElement.getAttribute("method");
                Map<String, Method> actionMethodMap = container.getControllerMethod(beanName);
                if (actionMethodMap == null) {
                    logger.warn(beanName + " is null");
                    continue;
                }
                Method method = actionMethodMap.get(actionMethodName);
                if (method == null) {
                    continue;
                }
                // 获取所有参数名称列表
                RequestParameters
                        requestParameters = method.getAnnotation(RequestParameters.class);
                if (requestParameters != null) {
                    String[] names = requestParameters.names().split(SYMBOL.COMMA);
                    List<String> parameterNameList = new ArrayList<String>(names.length);
                    for (String parameter : names) {
                        parameterNameList.add(parameter.trim());
                    }
                    invocableHandlerMethod.setParameterNameList(parameterNameList);
                }
                invocableHandlerMethod.setMethod(method);
                invocableHandlerMethod.setController(container.getBean(beanName));
                NodeList resultList = actionElement.getChildNodes();
                for (int i = 0; i < resultList.getLength(); i++) {
                    if (resultList.item(i).getNodeType() != 1) {
                        continue;
                    }
                    Element resultElement = (Element) resultList.item(i);
                    String resultName = resultElement.getAttribute("name");
                    if (CONSTANT.ERROR.equalsIgnoreCase(resultName.trim())) {
                        invocableHandlerMethod.setErrorUrl(resultElement
                                .getTextContent().trim());
                    } else if (CONSTANT.SUCCESS.equalsIgnoreCase(resultName.trim())) {
                        invocableHandlerMethod.setSuccessUrl(resultElement
                                .getTextContent().trim());
                    }
                }

                if (actionName.contains(SYMBOL.BIG_LEFT_PARENTHESIS) && actionName.contains(SYMBOL.BIG_RIGHT_PARENTHESIS)) {
                    Pair<String, List<String>> pathParameters = RegexUtility.getActionRegex(actionName);
                    invocableHandlerMethod.setActionRegex(pathParameters.getFirst());
                    invocableHandlerMethod.setPathParameterNameList(pathParameters.getSecond());
                    dynamicMapping.put(pathParameters.getFirst(), invocableHandlerMethod);
                } else {
                    mapping.put(actionName,
                            invocableHandlerMethod);
                }
            }
        } catch (Exception e) {
            logger.error("init action controller config error", e);
        }
    }

    @Override
    public ServletInvocableHandlerMethod getHandler(HttpServletRequest request) throws Exception {
        String actionKey = servletUtility.getActionKey(request);
        ServletInvocableHandlerMethod servletInvocableHandlerMethod= this.mapping.get(actionKey);
        if(servletInvocableHandlerMethod!=null){
            return servletInvocableHandlerMethod;
        }
        for(String regex:this.dynamicMapping.keySet()){
            if(RegexUtility.matches(actionKey,regex)){
                return this.dynamicMapping.get(regex);
            }
        }
        return null;
    }
}

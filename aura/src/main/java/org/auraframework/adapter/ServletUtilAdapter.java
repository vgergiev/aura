/*
 * Copyright (C) 2013 salesforce.com, inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.auraframework.adapter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.auraframework.Aura;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.DefDescriptor.DefType;
import org.auraframework.system.*;
import org.auraframework.system.AuraContext.Format;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.throwable.quickfix.QuickFixException;

/**
 * <p>
 * Service for responding to requests from a Aura Client.
 * </p>
 * Instances of all AuraServices should be retrieved from {@link Aura} </p> Note that this service is rather incomplete
 * and should be expanded to include more of the support routines from the servlets.
 */
public interface ServletUtilAdapter extends AuraAdapter {
    public static final String AURA_PREFIX = "aura.";
    public static final String CSRF_PROTECT = "while(1);\n";

    /**
     * Hook at the beginning of any resource request.
     *
     * This hook will be called after the initial search for a resource to allow the function to do any special
     * processing based on the resource.
     *
     * @param request the incoming request.
     * @param response the outgoing response.
     * @param resource the resource that we are handling.
     * @return false if the request has already been handled
     */
    boolean resourceServletGetPre(HttpServletRequest request, HttpServletResponse response, AuraResource resource);

    /**
     * Hook for the beginning of 'GET' action requests.
     *
     * @param request the incoming request.
     * @param response the outgoeing response
     * @return false if the request has already been handled
     */
    boolean actionServletGetPre(HttpServletRequest request, HttpServletResponse response) throws IOException;

    /**
     * Hook for the beginning of 'POST' action requests.
     *
     * @param request the incoming request.
     * @param response the outgoeing response
     * @return false if the request has already been handled
     */
    boolean actionServletPostPre(HttpServletRequest request, HttpServletResponse response) throws IOException;

    /**
     * Handle a servlet execption as well as we can.
     *
     * @param t the exception thrown.
     * @param quickfix are we in the middle of writing a quickfix.
     * @param context the current aura context
     * @param request the http request.
     * @param response the http response
     * @param written have we already written to the response.
     */
    void handleServletException(Throwable t, boolean quickfix, AuraContext context,
            HttpServletRequest request, HttpServletResponse response,
            boolean written) throws IOException;

    /**
     * Send a 404 page to the client.
     */
    void send404(ServletConfig config, HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;

    /**
     * Get the full set of scripts for the current context.
     */
    List<String> getScripts(AuraContext context) throws QuickFixException;

    /**
     * Get the full set of styles for the current context.
     */
    List<String> getStyles(AuraContext context) throws QuickFixException;

    /**
     * Get the base set of scripts for the current context.
     */
    List<String> getBaseScripts(AuraContext context) throws QuickFixException;

    /**
     * Get the base set of scripts for the top level namespace of the current context.
     */
    List<String> getNamespacesScripts(AuraContext context) throws QuickFixException;

    /**
     * Force a page to not be cached.
     *
     * This probably does not need to be overridden, but fits with the remaining functionality here.
     */
    void setNoCache(HttpServletResponse response);

    /**
     * Set a page to be cached for a 'short' period.
     */
    void setShortCache(HttpServletResponse response);

    /**
     * Set a page to cache for a 'long' time.
     */
    void setLongCache(HttpServletResponse response);

    /**
     * are we in production mode?
     */
    boolean isProductionMode(Mode mode);

    /**
     * Setup basic security headers.
     */
    void setCSPHeaders(DefDescriptor<?> top, HttpServletRequest req, HttpServletResponse rsp);

    /**
     * Get a content type for a format.
     */
    String getContentType(Format format);

    /**
     * Check if retrieving a def type is valid for a given mode.
     */
    boolean isValidDefType(DefType defType, Mode mode);

    Set<DefDescriptor<?>> verifyTopLevel(HttpServletRequest request, HttpServletResponse response,
            AuraContext context) throws IOException;
}

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
package org.auraframework.http;

import java.io.IOException;
import java.util.List;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.auraframework.Aura;
import org.auraframework.def.DefDescriptor;
import org.auraframework.http.RequestParam.StringParam;
import org.auraframework.system.AuraContext;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.throwable.quickfix.QuickFixException;

@SuppressWarnings("serial")
public abstract class AuraBaseServlet extends HttpServlet {
    public static final String AURA_PREFIX = "aura.";
    public static final String CSRF_PROTECT = "while(1);\n";

    /**
     * "Short" pages (such as manifest cookies and AuraFrameworkServlet pages) expire in 1 day.
     */
    public static final long SHORT_EXPIRE_SECONDS = 24L * 60 * 60;
    public static final long SHORT_EXPIRE = SHORT_EXPIRE_SECONDS * 1000;

    /**
     * "Long" pages (such as resources and cached HTML templates) expire in 45 days. We also use this to "pre-expire"
     * no-cache pages, setting their expiration a month and a half into the past for user agents that don't understand
     * Cache-Control: no-cache.
     * This is the same as ServletUtilAdapterImpl.java
     */
    public static final long LONG_EXPIRE = 45 * SHORT_EXPIRE;
    public static final String UTF_ENCODING = "UTF-8";
    public static final String HTML_CONTENT_TYPE = "text/html";
    public static final String JAVASCRIPT_CONTENT_TYPE = "text/javascript";
    public static final String MANIFEST_CONTENT_TYPE = "text/cache-manifest";
    public static final String CSS_CONTENT_TYPE = "text/css";
    public static final String SVG_CONTENT_TYPE = "image/svg+xml";

    /** Clickjack protection HTTP header */
    public static final String HDR_FRAME_OPTIONS = "X-FRAME-OPTIONS";
    /** Baseline clickjack protection level for HDR_FRAME_OPTIONS header */
    public static final String HDR_FRAME_SAMEORIGIN = "SAMEORIGIN";
    /** No-framing-at-all clickjack protection level for HDR_FRAME_OPTIONS header */
    public static final String HDR_FRAME_DENY = "DENY";
    /** Limited access for HDR_FRAME_OPTIONS */
    public static final String HDR_FRAME_ALLOWFROM = "ALLOW-FROM ";
    /**
     * Semi-standard HDR_FRAME_OPTIONS to have no restrictions.  Used because no
     * header at all is taken as an invitation for filters to add their own ideas.
     */
    public static final String HDR_FRAME_ALLOWALL = "ALLOWALL";

    protected static MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
    public static final String OUTDATED_MESSAGE = "OUTDATED";
    protected final static StringParam csrfToken = new StringParam(AURA_PREFIX + "token", 0, true);

    public static String getToken() {
        return Aura.getConfigAdapter().getCSRFToken();
    }

    public static void validateCSRF(String token) {
        Aura.getConfigAdapter().validateCSRFToken(token);
    }

    /**
     * Tell the browser to not cache.
     *
     * This sets several headers to try to ensure that the page will not be cached. Not sure if last modified matters
     * -goliver
     *
     * @param response the HTTP response to which we will add headers.
     */
    public static void setNoCache(HttpServletResponse response) {
        Aura.getServletUtilAdapter().setNoCache(response);
    }

    /**
     * Set a long cache timeout.
     *
     * This sets several headers to try to ensure that the page will be cached for a reasonable length of time. Of note
     * is the last-modified header, which is set to a day ago so that browsers consider it to be safe.
     *
     * @param response the HTTP response to which we will add headers.
     */
    public static void setLongCache(HttpServletResponse response) {
        Aura.getServletUtilAdapter().setLongCache(response);
    }

    /**
     * Set a 'short' cache timeout.
     *
     * This sets several headers to try to ensure that the page will be cached for a shortish length of time. Of note is
     * the last-modified header, which is set to a day ago so that browsers consider it to be safe.
     *
     * @param response the HTTP response to which we will add headers.
     */
    public static void setShortCache(HttpServletResponse response) {
        Aura.getServletUtilAdapter().setShortCache(response);
    }

    public AuraBaseServlet() {
        super();
    }

    /**
     * Check to see if we are in production mode.
     */
    protected boolean isProductionMode(Mode mode) {
        return Aura.getServletUtilAdapter().isProductionMode(mode);
    }

    public String getContentType(AuraContext.Format format) {
        return Aura.getServletUtilAdapter().getContentType(format);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    /**
     * Sets mandatory headers, notably for anti-clickjacking.
     */
    protected void setBasicHeaders(DefDescriptor<?> top, HttpServletRequest req, HttpServletResponse rsp) {
        Aura.getServletUtilAdapter().setCSPHeaders(top, req, rsp);
    }


    @Deprecated
    public static List<String> getScripts() throws QuickFixException {
        return Aura.getServletUtilAdapter().getScripts(Aura.getContextService().getCurrentContext());
    }

    @Deprecated
    public static List<String> getStyles() throws QuickFixException {
        return Aura.getServletUtilAdapter().getStyles(Aura.getContextService().getCurrentContext());
    }

    @Deprecated
    public static List<String> getBaseScripts(AuraContext context) throws QuickFixException {
        return Aura.getServletUtilAdapter().getBaseScripts(context);
    }

    @Deprecated
    public static List<String> getNamespacesScripts(AuraContext context) throws QuickFixException {
        return Aura.getServletUtilAdapter().getNamespacesScripts(context);
    }

    @Deprecated
    protected void send404(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        Aura.getServletUtilAdapter().send404(getServletConfig(), request, response);
    }

    @Deprecated
    public void handleServletException(Throwable t, boolean quickfix, AuraContext context,
            HttpServletRequest request, HttpServletResponse response,
            boolean written) throws IOException {
        Aura.getServletUtilAdapter().handleServletException(t, quickfix, context, request, response, written);
    }
}

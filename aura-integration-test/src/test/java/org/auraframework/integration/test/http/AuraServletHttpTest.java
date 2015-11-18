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
package org.auraframework.integration.test.http;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.auraframework.Aura;
import org.auraframework.adapter.ContentSecurityPolicy;
import org.auraframework.adapter.DefaultContentSecurityPolicy;
import org.auraframework.def.ApplicationDef;
import org.auraframework.def.ComponentDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.http.AuraBaseServlet;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.test.adapter.MockConfigAdapter;
import org.auraframework.test.client.UserAgent;
import org.auraframework.test.util.AuraHttpTestCase;
import org.auraframework.util.json.JsFunction;
import org.auraframework.util.json.JsonEncoder;
import org.auraframework.util.json.JsonReader;
import org.auraframework.util.test.annotation.ThreadHostileTest;
import org.auraframework.util.test.annotation.UnAdaptableTest;

/**
 * Automation to verify the handling of AuraServlet requests.
 *
 *
 * @since 0.0.139
 */
public class AuraServletHttpTest extends AuraHttpTestCase {

    private static class MockCsp implements ContentSecurityPolicy {
        private final String[] ancestors;

        public MockCsp(String... ancestors) {
            this.ancestors = ancestors;
        }

        @Override
        public String getCspHeaderValue() {
            return DefaultContentSecurityPolicy.buildHeaderNormally(this);
        }

        @Override
        public Collection<String> getFrameAncestors() {
            if (ancestors == null) {
                return null;
            }
            List<String> list = new ArrayList<>(ancestors.length);
            for (String item : ancestors) {
                list.add(item);
            }
            return list;
        }

        @Override
        public Collection<String> getFrameSources() {
            return new ArrayList<>(0);
        }

        @Override
        public Collection<String> getScriptSources() {
            List<String> list = new ArrayList<>(1);
            list.add(null);
            return list;
        }

        @Override
        public Collection<String> getStyleSources() {
            List<String> list = new ArrayList<>(1);
            list.add(null);
            return list;
        }

        @Override
        public Collection<String> getConnectSources() {
            List<String> list = new ArrayList<>(2);
            list.add("www.itrustu.com/");
            list.add("www.also.com/other");
            return list;
        }

        @Override
        public Collection<String> getFontSources() {
            return null;
        }

        @Override
        public Collection<String> getDefaultSources() {
            List<String> list = new ArrayList<>(1);
            list.add(null);
            return list;
        }

        @Override
        public Collection<String> getImageSources() {
            return null;
        }

        @Override
        public Collection<String> getObjectSources() {
            return new ArrayList<>(0);
        }

        @Override
        public Collection<String> getMediaSources() {
            return null;
        }

        @Override
        public String getReportUrl() {
            return "http://doesnt.matter.com/";
        }
    }

    public AuraServletHttpTest(String name) {
        super(name);
    }

    /**
     * Test for W-2063110 this test is to verify the order of actions and context in the response we used to have
     * context before actions, now it's the opposite
     */
    public void testPostRawResponseSimpleAction() throws Exception {
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("param", "some string");
        ServerAction a = new ServerAction(
                "java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getString",
                actionParams);
        a.run();
        String rawRes = a.getrawResponse();
        Integer posActions = rawRes.indexOf("actions");
        Integer posContex = rawRes.indexOf("context");
        assertTrue(posActions < posContex);
    }

    /**
     * When we request a component from the server we should get back it's component class, but only a single occurrence
     * of it to minimize payload.
     */
    public void testGetComponentActionReturnsSingleComponentClass() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component></aura:component>");

        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("name", cmpDesc.getQualifiedName());
        ServerAction a = new ServerAction(
                "java://org.auraframework.impl.controller.ComponentController/ACTION$getComponent",
                actionParams);
        a.run();
        String rawRes = a.getrawResponse();

        int firstOccurrence = rawRes.indexOf("componentClass");
        int lastOccurrence = rawRes.lastIndexOf("componentClass");
        assertTrue("Component class should be returned in server response when requesting component",
                firstOccurrence != -1);
        assertTrue("Server response should only return a single componentClass for a component, but got <" + rawRes
                + ">", firstOccurrence == lastOccurrence);
    }

    public void testMulitpleActionsInOnePost() {
        ArrayList<String> qNameList = new ArrayList<>();
        ArrayList<Map<String, Object>> actionParamsArrayList = new ArrayList<>();

        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("param", "some string");
        qNameList.add("java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getString");
        actionParamsArrayList.add(actionParams);

        Map<String, Object> actionParams1 = new HashMap<>();
        actionParams1.put("param", 6);
        qNameList.add("java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getInt");
        actionParamsArrayList.add(actionParams1);

        ServerAction a = new ServerAction(qNameList, actionParamsArrayList);
        a.run();
        assertTrue("The response does not have the expected number of actions", a.getReturnValueList().size() == 2);
        assertTrue(a.getReturnValueList().get(0).equals("some string")
                && a.getReturnValueList().get(1).equals(new BigDecimal(6)));

    }

    /**
     * Check a post context.
     */
    public void testPostContext() throws Exception {
        Map<String, Object> message = new HashMap<>();
        Map<String, Object> actionInstance = new HashMap<>();
        actionInstance.put("descriptor",
                "java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getString");
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("param", "some string");
        actionInstance.put("params", actionParams);
        @SuppressWarnings("rawtypes")
        Map[] actions = { actionInstance };
        message.put("actions", actions);

        String jsonMessage = JsonEncoder.serialize(message);

        Map<String, String> params = new HashMap<>();
        params.put("message", jsonMessage);
        params.put("aura.token", getCsrfToken());

        DefDescriptor<ApplicationDef> app = Aura.getDefinitionService().getDefDescriptor(
                "auratest:test_SimpleServerRenderedPage", ApplicationDef.class);
        params.put("aura.context", getAuraTestingUtil().buildContextForPost(Mode.DEV, app));

        HttpPost post = obtainPostMethod("/aura", params);
        HttpResponse httpResponse = perform(post);
        int statusCode = getStatusCode(httpResponse);
        String response = getResponseBody(httpResponse);
        post.releaseConnection();

        if (HttpStatus.SC_OK != statusCode) {
            fail(String.format("Unexpected status code <%s>, expected <%s>, response:%n%s", statusCode,
                    HttpStatus.SC_OK, response));
        }
        new JsonReader().read(response.substring(AuraBaseServlet.CSRF_PROTECT.length()));
    }

    /**
     * This is actually an invalid test.
     *
     */
    public void testPostWithOldLastMod() throws Exception {
        Map<String, Object> message = new HashMap<>();
        Map<String, Object> actionInstance = new HashMap<>();
        actionInstance.put("descriptor",
                "java://org.auraframework.components.test.java.controller.JavaTestController/ACTION$getString");
        Map<String, Object> actionParams = new HashMap<>();
        actionParams.put("param", "some string");
        actionInstance.put("params", actionParams);
        @SuppressWarnings("rawtypes")
        Map[] actions = { actionInstance };
        message.put("actions", actions);

        String jsonMessage = JsonEncoder.serialize(message);

        Map<String, String> params = new HashMap<>();
        params.put("message", jsonMessage);
        params.put("aura.token", getCsrfToken());
        DefDescriptor<ApplicationDef> app = Aura.getDefinitionService().getDefDescriptor(
                "auratest:test_SimpleServerRenderedPage", ApplicationDef.class);
        String fwuid = getAuraTestingUtil().modifyUID(Aura.getConfigAdapter().getAuraFrameworkNonce());
        params.put("aura.context", getAuraTestingUtil().buildContextForPost(Mode.DEV, app, null, fwuid, null, null));

        HttpPost post = obtainPostMethod("/aura", params);
        HttpResponse httpResponse = perform(post);
        int statusCode = getStatusCode(httpResponse);
        String response = getResponseBody(httpResponse);
        post.releaseConnection();

        assertEquals("Status code should be 200", HttpStatus.SC_OK, statusCode);

        assertTrue("response not wrapped with ERROR marker: " + response,
                response.startsWith(AuraBaseServlet.CSRF_PROTECT + "*/") && response.endsWith("/*ERROR*/"));
        response = response.substring(AuraBaseServlet.CSRF_PROTECT.length() + 2,
                response.length() - "/*ERROR*/".length());
        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) new JsonReader().read(response);
        assertEquals(true, json.get("exceptionEvent"));
        @SuppressWarnings("unchecked")
        Map<String, Object> eventJson = (Map<String, Object>) json.get("event");
        assertEquals("markup://aura:clientOutOfSync", eventJson.get("descriptor"));
        Object f = json.get("defaultHandler");
        assertEquals(JsFunction.class, f.getClass());
        assertEquals("try{$A.clientService.setOutdated()}catch(e){$L.clientService.setOutdated()}",
                ((JsFunction) f).getBody());
    }

    private void assertNoCacheRequest(String inputUrl, String expectedRedirect) throws Exception {
        HttpGet get = obtainGetMethod(inputUrl, false);
        HttpResponse response = perform(get);
        EntityUtils.consume(response.getEntity());
        get.releaseConnection();

        assertEquals(HttpStatus.SC_MOVED_TEMPORARILY, getStatusCode(response));
        String location = response.getFirstHeader(HttpHeaders.LOCATION).getValue();
        assertTrue("Location is not absolute (but should be by spec): " + location,
                location.matches("^https?://[a-zA-Z0-9_\\-.]*:[0-9]*(/.*)?$"));
        int index = location.indexOf(':');
        index = location.indexOf(':', index + 1) + 1; // find the port-separating colon
        while (location.charAt(index) >= '0' && location.charAt(index) <= '9') {
            index++;
        }
        assertEquals("Wrong URI path", expectedRedirect, location.substring(index));
        assertEquals("no-cache, no-store", response.getFirstHeader(HttpHeaders.CACHE_CONTROL).getValue());
        assertEquals("no-cache", response.getFirstHeader(HttpHeaders.PRAGMA).getValue());
        assertDefaultAntiClickjacking(response, false, false); // Redirects don't have XFO/CSP guarding
    }

    /**
     * nocache in the request will redirect to the input url (minus the protocol and host)
     */
    public void testNoCache() throws Exception {
        assertNoCacheRequest(String.format("/aura?aura.tag&nocache=%s", URLEncoder.encode(
                "http://any.host/m?aura.mode=PROD&aura.format=HTML#someidinhere?has=someparam", "UTF-8")),
                "/m?aura.mode=PROD&aura.format=HTML#someidinhere?has=someparam");
    }

    /**
     * This handles a Chrome (or maybe WebKit) bug where a Location semi-correctly beginning with a double or more slash
     * is taken as a hostname (i.e. as if it were http: + the location),
     *
     */
    public void testNoCacheDoubleSlash() throws Exception {
        assertNoCacheRequest(String.format("/aura?aura.tag&nocache=%s",
                URLEncoder.encode("http://any.host//www.badnews.com", "UTF-8")),
                "//www.badnews.com");
        assertNoCacheRequest("/aura?aura.tag&nocache=/", "/");
    }

    public void testNoCacheNoFragment() throws Exception {
        assertNoCacheRequest(
                String.format("/aura?aura.tag&nocache=%s", URLEncoder.encode("http://any.host/m?chatter", "UTF-8")),
                "/m?chatter");
    }

    public void testNoCacheNoQuery() throws Exception {
        assertNoCacheRequest(
                String.format("/aura?aura.tag&nocache=%s",
                        URLEncoder.encode("http://any.host/m#someid?param=extra", "UTF-8")), "/m#someid?param=extra");
    }

    public void testNoCacheNoTag() throws Exception {
        HttpGet get = obtainGetMethod("/aura?aura.tag&nocache");
        HttpResponse response = perform(get);

        assertEquals(HttpStatus.SC_OK, getStatusCode(response));
        String responseText = getResponseBody(response);
        assertTrue("Expected tag error in: " + responseText,
                responseText.contains("Invalid request, tag must not be empty"));
        get.releaseConnection();
    }

    /**
     * Verify https is preserved in the nocache redirect
     */
    public void testNoCacheHttpsRedirect() throws Exception {
        String inputUrl = String.format("/aura?aura.tag&nocache=%s", URLEncoder.encode(
                "https://any.host/m?aura.mode=PROD&aura.format=HTML#someidinhere?has=someparam", "UTF-8"));
        HttpGet get = obtainGetMethod(inputUrl, false);
        HttpResponse response = perform(get);
        EntityUtils.consume(response.getEntity());
        get.releaseConnection();
        String location = response.getFirstHeader(HttpHeaders.LOCATION).getValue();

        assertTrue("Location should start with https but was: " + location, location.startsWith("https:"));
    }

    /**
     * Verify http is preserved in the nocache redirect
     */
    public void testNoCacheHttpRedirect() throws Exception {
        String inputUrl = String.format("/aura?aura.tag&nocache=%s", URLEncoder.encode(
                "http://any.host/m?aura.mode=PROD&aura.format=HTML#someidinhere?has=someparam", "UTF-8"));
        HttpGet get = obtainGetMethod(inputUrl, false);
        HttpResponse response = perform(get);
        EntityUtils.consume(response.getEntity());
        get.releaseConnection();
        String location = response.getFirstHeader(HttpHeaders.LOCATION).getValue();

        assertTrue("Location should start with http but was: " + location, location.startsWith("http:"));
    }

    /**
     * Verify http redirect URL with plus can be correctly decoded
     */
    public void testRedirectUrlWithPlus() throws Exception {
        String inputUrl = String.format("/aura?aura.tag&nocache=%s", URLEncoder.encode(
                "http://any.host/m?foo=bar+bar", "UTF-8"));
        HttpGet get = obtainGetMethod(inputUrl, false);
        HttpResponse response = perform(get);
        EntityUtils.consume(response.getEntity());
        get.releaseConnection();
        String location = response.getFirstHeader(HttpHeaders.LOCATION).getValue();

        String actual = location.substring(location.indexOf('?') + 1);
        assertEquals("foo=bar+bar", actual);
    }

    public void testHTMLTemplateCaching() throws Exception {
        // An application with isOnePageApp set to true
        DefDescriptor<ApplicationDef> desc = addSourceAutoCleanup(ApplicationDef.class,
                "<aura:application isOnePageApp='true'></aura:application>");

        // Expect the get request to be set for long cache
        assertResponseSetToLongCache(String.format("/%s/%s.app", desc.getNamespace(), desc.getName()));

        // An application with isOnePageApp set to false
        desc = addSourceAutoCleanup(ApplicationDef.class, "<aura:application isOnePageApp='false'></aura:application>");
        // Expect the get request to be set for no caching
        assertResponseSetToNoCache(String.format("/%s/%s.app", desc.getNamespace(), desc.getName()));

        // An application with no specification
        desc = addSourceAutoCleanup(ApplicationDef.class, "<aura:application></aura:application>");
        // Expect the get request to be set for no caching
        assertResponseSetToNoCache(String.format("/%s/%s.app", desc.getNamespace(), desc.getName()));

        // A component and AuraBaseServlet.isManifestEnabled() is false because
        // UserAgent is not "AppleWebKit" based
        setHttpUserAgent(UserAgent.EMPTY.getUserAgentString());
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component ></aura:component>");
        // Expect the get request to be set for long cache
        assertResponseSetToLongCache(String.format("/%s/%s.cmp", cmpDesc.getNamespace(), cmpDesc.getName()));
    }

    // following 5 tests are mainly for mapping between custom CSP to X-FRAME-OPTIONS
    // the logic is in AuraBaseServlet.setBasicHeaders()
    // note these tests are for aura stand alone only, when running in core, it has different CSP (with more script-src
    // etc)

    // 1.if we set ancestor resources with more than one url('self' counts as url), we won't create X-FRAME-OPTIONS
    @ThreadHostileTest("swaps config adapter")
    @UnAdaptableTest("CSP is different between aura-stand-alone and core")
    public void testSpecialCspMultipleAncestors() throws Exception {
        Header[] headers = doSpecialCspTest("'self' www.itrustu.com/frame www.also.com/other",
                null, "www.itrustu.com/frame", "www.also.com/other");
        assertEquals("wrong number of X-FRAME-OPTIONS header lines", 0, headers.length);
    }

    // 2.if we set ancestor resources with one url (without wildcard), that url will get written into X-FRAME-OPTIONS
    @ThreadHostileTest("swaps config adapter")
    public void testSpecialCspSingleAncestor() throws Exception {
        Header[] headers = doSpecialCspTest("www.itrustu.com/frame", "www.itrustu.com/frame");
        assertEquals("wrong number of X-FRAME-OPTIONS header lines", 1, headers.length);
        assertEquals("ALLOW-FROM www.itrustu.com/frame", headers[0].getValue());
    }

    // 3.if we set ancestor resources with protocal like url, we set ALLOWALL for X-FRAME-OPTIONS
    @ThreadHostileTest("swaps config adapter")
    public void testSpecialCspProtocolAncestor() throws Exception {
        Header[] headers = doSpecialCspTest("https:", "https:");
        assertEquals("wrong number of X-FRAME-OPTIONS header lines", 1, headers.length);
        assertEquals("ALLOWALL", headers[0].getValue());
    }

    // 4.if we set ancestor with one wildcard url, we set ALLOWALL for X-FRAME-OPTIONS
    @ThreadHostileTest("swaps config adapter")
    public void testSpecialCspWildcardAncestor() throws Exception {
        Header[] headers = doSpecialCspTest("https://*.foo.com", "https://*.foo.com");
        assertEquals("wrong number of X-FRAME-OPTIONS header lines", 1, headers.length);
        assertEquals("ALLOWALL", headers[0].getValue());
    }

    // 5.if we set ancestor resources with null, DENY get written into X-FRAME-OPTIONS
    @ThreadHostileTest("swaps config adapter")
    public void testSpecialCspDeniedAncestor() throws Exception {
        Header[] headers = doSpecialCspTest("'none'");
        assertEquals("wrong number of X-FRAME-OPTIONS header lines", 1, headers.length);
        assertEquals("DENY", headers[0].getValue());
    }

    // 6.if we set ancestor resources with [null], SAMEORIGIN get written into X-FRAME-OPTIONS
    @ThreadHostileTest("swaps config adapter")
    public void testSpecialCspSameOriginAncestor() throws Exception {
        Header[] headers = doSpecialCspTest("'self'", (String) null);
        assertEquals("wrong number of X-FRAME-OPTIONS header lines", 1, headers.length);
        assertEquals("SAMEORIGIN", headers[0].getValue());
    }

    // 7.if we set ancestor resources with null [], we won't change X-FRAME-OPTIONS
    @ThreadHostileTest("swaps config adapter")
    public void testSpecialCspAnyAncestor() throws Exception {
        Header[] headers = doSpecialCspTest("*", (String[]) null);
        assertEquals("wrong number of X-FRAME-OPTIONS header lines", 0, headers.length);
    }

    public void testHTMLTemplateCachingWhenAppCacheIsEnable() throws Exception {
        setHttpUserAgent(UserAgent.GOOGLE_CHROME.getUserAgentString());

        // An application with isOnePageApp set to true and useAppcache set to
        // true
        // isOnePageApp overrides useAppCache specification
        DefDescriptor<ApplicationDef> desc = addSourceAutoCleanup(ApplicationDef.class,
                "<aura:application isOnePageApp='true' useAppcache='true'></aura:application>");
        // Expect the get request to be set for long cache
        assertResponseSetToLongCache(String.format("/%s/%s.app", desc.getNamespace(), desc.getName()));

        // An application with useAppcache set to true and no specification for
        // isOnePageApp
        desc = addSourceAutoCleanup(ApplicationDef.class, "<aura:application useAppcache='true'></aura:application>");
        // Expect the get request to be set for no caching
        assertResponseSetToNoCache(String.format("/%s/%s.app", desc.getNamespace(), desc.getName()));

        // A component and AuraBaseServlet.isManifestEnabled() is false
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component ></aura:component>");
        // Expect the get request to be set for long cache
        assertResponseSetToLongCache(String.format("/%s/%s.cmp", cmpDesc.getNamespace(), cmpDesc.getName()));
    }

    /**
     * Wiggle factor.
     *
     * This is intended to allow for variance between the local date and the server date, along with any latency that
     * might occur. Currently it is set to 1 hour, which should be more than enough to account for offsets, but short
     * enough so that we don't really care.
     */
    private final static long WIGGLE_FACTOR = (1000L * 60 * 60 * 1);

    /**
     * Submit a request and check that the 'long cache' is set correctly.
     *
     * See documentation for {@link #WIGGLE_FACTOR}.
     *
     * @param url the url
     */
    private void assertResponseSetToLongCache(String url) throws Exception {
        Date expected = new Date(System.currentTimeMillis() + AuraBaseServlet.LONG_EXPIRE - WIGGLE_FACTOR);

        HttpGet get = obtainGetMethod(url);
        HttpResponse response = perform(get);

        assertEquals("Failed to execute request successfully.", HttpStatus.SC_OK, getStatusCode(response));

        assertEquals("Expected response to be marked for long cache",
                String.format("max-age=%s, public", AuraBaseServlet.LONG_EXPIRE / 1000),
                response.getFirstHeader(HttpHeaders.CACHE_CONTROL).getValue());
        assertDefaultAntiClickjacking(response, true, true);
        String expiresHdr = response.getFirstHeader(HttpHeaders.EXPIRES).getValue();
        Date expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).parse(expiresHdr);
        //
        // We show all of the related dates/strings to help with debugging.
        //
        assertTrue(String.format("Expires header is earlier than expected. Expected !before %s, got %s (%s).",
                expected, expires, expiresHdr), !expires.before(expected));

        get.releaseConnection();
    }

    /**
     * Submit a request and check that the 'no cache' is set correctly.
     *
     * We are very generous with the expires time here, as we really don't care other than to have it well in the past.
     *
     * @param url the url path.
     */
    private void assertResponseSetToNoCache(String url) throws Exception {
        Date expected = new Date(System.currentTimeMillis());
        HttpGet get = obtainGetMethod(url);
        HttpResponse response = perform(get);
        assertEquals("Failed to execute request successfully.", HttpStatus.SC_OK, getStatusCode(response));

        assertEquals("Expected response to be marked for no-cache", "no-cache, no-store",
                response.getFirstHeader(HttpHeaders.CACHE_CONTROL).getValue());
        assertEquals("no-cache", response.getFirstHeader(HttpHeaders.PRAGMA).getValue());
        assertDefaultAntiClickjacking(response, true, true);

        String expiresHdr = response.getFirstHeader(HttpHeaders.EXPIRES).getValue();
        Date expires = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).parse(expiresHdr);
        //
        // We show all of the related dates/strings to help with debugging.
        //
        assertTrue(String.format("Expires header should be in the past. Expected before %s, got %s (%s).",
                expected, expires, expiresHdr), expires.before(expected));

        EntityUtils.consume(response.getEntity());
        get.releaseConnection();
    }

    /** Runs a test with special CSP */
    private Header[] doSpecialCspTest(String expectCspAncestors, String... ancestors) throws Exception {
        ContentSecurityPolicy mockCsp = new MockCsp(ancestors);

        MockConfigAdapter mci = getMockConfigAdapter();

        try {
            mci.setContentSecurityPolicy(mockCsp);

            // An application with isOnePageApp set to true
            DefDescriptor<ApplicationDef> desc = addSourceAutoCleanup(ApplicationDef.class,
                    "<aura:application isOnePageApp='true'></aura:application>");

            HttpGet get = obtainGetMethod(String.format("/%s/%s.app", desc.getNamespace(), desc.getName()));
            HttpResponse response = perform(get);

            // Check X-FRAME-OPTIONS
            Header[] headers = response.getHeaders("X-FRAME-OPTIONS");

            // And CSP
            Map<String, String> csp = getCSP(response);
            assertEquals("frame-ancestors is wrong", expectCspAncestors, csp.get("frame-ancestors"));
            assertEquals("script-src is wrong", "'self'", csp.get("script-src"));
            assertEquals("style-src is wrong", "'self'", csp.get("style-src"));
            assertEquals("connect-src is wrong", "www.itrustu.com/ www.also.com/other", csp.get("connect-src"));
            assertEquals("font-src is wrong", "*", csp.get("font-src"));
            assertEquals("img-src is wrong", "*", csp.get("img-src"));
            assertEquals("object-src is wrong", "'none'", csp.get("object-src"));
            assertEquals("media-src is wrong", "*", csp.get("media-src"));
            assertEquals("default-src is wrong", "'self'", csp.get("default-src"));

            return headers;
        } finally {
            mci.setContentSecurityPolicy(null);
        }

    }

    /**
     * Verify the Script tag to fetch the Aura Framework JS has nonce. The initial get request for an application gets a
     * template as response. Part of the template response should be a script tag which fetches the Aura FW JS. The URL
     * for the js file should have nonce indicating the last mod of the JS group.
     *
     * @throws Exception
     */
    public void testJSFrameworkUrlHasNonce() throws Exception {
        DefDescriptor<ApplicationDef> desc = addSourceAutoCleanup(ApplicationDef.class,
                "<aura:application render='client'></aura:application>");
        HttpGet get = obtainGetMethod(String.format("/%s/%s.app", desc.getNamespace(), desc.getName()));
        HttpResponse response = perform(get);
        assertEquals(HttpStatus.SC_OK, getStatusCode(response));
        // Fetch the latest timestamp of the JS group and construct URL for DEV mode.
        String expectedFWUrl = String.format("/auraFW/javascript/%s/aura_dev.js",
                Aura.getConfigAdapter().getAuraFrameworkNonce());
        String scriptTag = String.format("<script src=\"%s\" ></script>", expectedFWUrl);
        assertTrue("Expected Aura FW Script tag not found. Expected to see: " + scriptTag,
                getResponseBody(response).contains(scriptTag));

        assertDefaultAntiClickjacking(response, true, true);
        get.releaseConnection();
    }

    public void testGetUnhandledError() throws Exception {
        DefDescriptor<ApplicationDef> desc = addSourceAutoCleanup(ApplicationDef.class,
                "<aura:application><aura:attribute name='bah'/></aura:application>");
        HttpGet get = obtainGetMethod(String.format("/%s/%s.app", desc.getNamespace(), desc.getName()));
        HttpResponse httpResponse = perform(get);
        assertEquals(HttpStatus.SC_OK, getStatusCode(httpResponse));
        String response = getResponseBody(httpResponse);
        assertTrue("Expected null descriptor error message but got: " + response,
                response.contains("descriptor is null"));
        get.releaseConnection();
    }

    /**
     * Verify providing invalid DefDescriptor format to the aura.tag param results in the proper handled Exception and
     * not an AuraUnhandledException, which results in a Gack on SFDC.
     */
    public void testInvalidDefDescriptorFormat() throws Exception {
        String url = String.format("/aura?aura.tag=foo:bar:baz");
        HttpGet get = obtainGetMethod(url);
        HttpResponse httpResponse = perform(get);
        assertEquals(HttpStatus.SC_OK, getStatusCode(httpResponse));
        String response = getResponseBody(httpResponse);
        assertTrue("Expected 'SystemErrorException: Invalid Descriptor Format' but got: " + response,
                response.contains("SystemErrorException: Invalid Descriptor Format: foo:bar:baz"));
        assertFalse("Invalid aura.tag input should not result in an AuraUnhandledException. " + response,
                response.contains("AuraUnhandledException: Unable to process your request"));
        get.releaseConnection();
    }

    /**
     * Verify providing invalid DefDescriptor format to the aura.tag param results in the proper handled Exception and
     * not an AuraUnhandledException, which results in a Gack on SFDC.
     */
    public void testInvalidDefDescriptorFormatExploit() throws Exception {
        String url = "/aura?aura.tag=one:one%3Csvg%3E%3Cscript%3E0%3C1%3Ealert(document.domain)%3C%2Fscript%3E.app";

        // test in prod.
        HttpGet get = obtainGetMethod(url+"&aura.mode=PROD");
        HttpResponse httpResponse = perform(get);

        assertEquals(HttpStatus.SC_OK, getStatusCode(httpResponse));
        String response = getResponseBody(httpResponse);
        assertTrue("Expected 'Invalid Descriptor Format' but got: " + response,
                response.contains("Invalid Descriptor Format: one:one&lt;svg&gt;&lt;script&gt;"));
        assertFalse("Invalid aura.tag input should not result in an AuraUnhandledException. " + response,
                response.contains("AuraUnhandledException: Unable to process your request"));
        get.releaseConnection();

        // and in dev.
        get = obtainGetMethod(url+"&aura.mode=DEV");
        httpResponse = perform(get);
        assertEquals(HttpStatus.SC_OK, getStatusCode(httpResponse));
        response = getResponseBody(httpResponse);
        assertTrue("Expected 'Invalid Descriptor Format' but got: " + response,
                response.contains("Invalid Descriptor Format: one:one&lt;svg&gt;&lt;script&gt;"));
        assertFalse("Invalid aura.tag input should not result in an AuraUnhandledException. " + response,
                response.contains("AuraUnhandledException: Unable to process your request"));
        get.releaseConnection();
    }
}

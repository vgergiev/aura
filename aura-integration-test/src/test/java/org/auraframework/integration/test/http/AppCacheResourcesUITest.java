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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.auraframework.Aura;
import org.auraframework.def.ApplicationDef;
import org.auraframework.def.ComponentDef;
import org.auraframework.def.ControllerDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.Definition;
import org.auraframework.def.StyleDef;
import org.auraframework.service.ContextService;
import org.auraframework.system.AuraContext;
import org.auraframework.system.AuraContext.Authentication;
import org.auraframework.system.AuraContext.Format;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.system.Source;
import org.auraframework.test.controller.TestLoggingAdapterController;
import org.auraframework.test.util.WebDriverTestCase;
import org.auraframework.test.util.WebDriverUtil.BrowserType;
import org.auraframework.util.AuraTextUtil;
import org.auraframework.util.test.annotation.FreshBrowserInstance;
import org.auraframework.util.test.annotation.ThreadHostileTest;
import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

/**
 * Tests for AppCache functionality by watching the requests received at the server and verifying that the updated
 * content is being used by the browser. AppCache only works for WebKit browsers.
 * 
 * @since 0.0.224
 */
@FreshBrowserInstance
@ThreadHostileTest("TestLoggingAdapter not thread-safe")
public class AppCacheResourcesUITest extends WebDriverTestCase {
    private final boolean debug = false;

    private final static String COOKIE_NAME = "%s_%s_%s_lm";
    private final static String TOKEN = "@@@TOKEN@@@";

    private static final String AURA = "aura";

    private enum Status {
        UNCACHED, IDLE, CHECKING, DOWNLOADING, UPDATEREADY, OBSOLETE;
    }

    private String appName;
    private String namespace;
    private String cmpName;

    public AppCacheResourcesUITest(String name) {
        super(name);
    }

    @Override
    public void perBrowserSetUp() {
        super.perBrowserSetUp();
        auraUITestingUtil.setTimeoutInSecs(60);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        namespace = "appCacheResourcesUITest" + getAuraTestingUtil().getNonce();
        appName = "cacheapplication";
        cmpName = "cachecomponent";

        DefDescriptor<ComponentDef> cmpDesc = createDef(ComponentDef.class, String.format("%s:%s", namespace, cmpName),
                "<aura:component>"
                        + "<aura:attribute name='output' type='String'/>"
                        + "<div class='clickableme' onclick='{!c.cssalert}'>@@@TOKEN@@@</div>"
                        + "<div class='attroutput'>{!v.output}</div>"
                        + "</aura:component>");

        createDef(
                ControllerDef.class,
                String.format("%s://%s.%s", DefDescriptor.JAVASCRIPT_PREFIX, namespace, cmpName),
                "{ cssalert:function(c){"
                        + "function getStyle(elem, style){"
                        + "var val = '';"
                        + "if(document.defaultView && document.defaultView.getComputedStyle){"
                        + "val = document.defaultView.getComputedStyle(elem, '').getPropertyValue(style);"
                        + "} else if(elem.currentStyle){"
                        + "style = style.replace(/\\-(\\w)/g, function (s, ch){"
                        + "return ch.toUpperCase();"
                        + "});"
                        + "val = elem.currentStyle[style];"
                        + "}"
                        + "return val;"
                        + "};"
                        + "var style = getStyle(c.getElement(),'background-image');"
                        + "c.set('v.output','@@@TOKEN@@@' + style.substring(style.lastIndexOf('?')+1,style.lastIndexOf(')'))"
                        + "+ ($A.test ? $A.test.dummyFunction() : '@@@TOKEN@@@'));"
                        + "}}");

        createDef(
                ApplicationDef.class,
                String.format("%s:%s", namespace, appName),
                String.format("<aura:application useAppcache='true' render='client'>"
                        + "<%s:%s/>" + "</aura:application>", namespace, cmpDesc.getName()));
    }

    /**
     * Opening cached app will only query server for the manifest and the component load. BrowserType.SAFARI is disabled
     * : W-2367702
     */
    @TargetBrowsers({ BrowserType.GOOGLECHROME, BrowserType.IPAD, BrowserType.IPHONE })
    public void testNoChanges() throws Exception {
        List<Request> logs = loadMonitorAndValidateApp(TOKEN, TOKEN, "", TOKEN);
        assertRequests(getExpectedInitialRequests(), logs);
        assertAppCacheStatus(Status.IDLE);

        // only expect a fetch for the manifest and the initAsync component load
        logs = loadMonitorAndValidateApp(TOKEN, TOKEN, "", TOKEN);
        List<Request> expected = Lists.newArrayList(new Request("/auraResource", "manifest", 200));
        assertRequests(expected, logs);
        assertAppCacheStatus(Status.IDLE);
    }

    /**
     * Opening cached app that had a prior cache error will reload the app. BrowserType.SAFARI is disabled : W-2367702
     */
    @TargetBrowsers({ BrowserType.GOOGLECHROME, BrowserType.IPAD, BrowserType.IPHONE })
    // TODO(W-2701964): Flapping in autobuilds, needs to be revisited
    @Flapper
    public void testCacheError() throws Exception {
        List<Request> logs = loadMonitorAndValidateApp(TOKEN, TOKEN, "", TOKEN);
        assertRequests(getExpectedInitialRequests(), logs);
        assertAppCacheStatus(Status.IDLE);

        Date expiry = new Date(System.currentTimeMillis() + 60000);
        String cookieName = getManifestCookieName();
        updateCookie(cookieName, "error", expiry, "/");

        logs = loadMonitorAndValidateApp(TOKEN, TOKEN, "", TOKEN);
        List<Request> expectedChange = Lists.newArrayList();
        expectedChange.add(new Request("/auraResource", "manifest", 404)); // reset
        expectedChange.add(new Request(getUrl(), null, 302)); // hard refresh
        switch (getBrowserType()) {
        case GOOGLECHROME:
            expectedChange.add(new Request(3, "/auraResource", "manifest", 200));
            expectedChange.add(new Request(2, getUrl(), null, 200));
            break;
        default:
            expectedChange.add(new Request("/auraResource", "manifest", 200));
            expectedChange.add(new Request(getUrl(), null, 200));
            expectedChange.add(new Request("/auraResource", "css", 200));
            expectedChange.add(new Request("/auraResource", "js", 200));
        }
        assertRequests(expectedChange, logs);
        assertAppCacheStatus(Status.IDLE);
        // There may be a varying number of requests, depending on when the initial manifest response is received.
        Cookie cookie = getDriver().manage().getCookieNamed(cookieName);
        assertFalse("Manifest cookie was not changed " + cookie.getValue(), "error".equals(cookie.getValue()));
    }

    /**
     * Opening uncached app that had a prior cache error will have limited caching. BrowserType.SAFARI is disabled :
     * W-2367702
     */
    @TargetBrowsers({ BrowserType.GOOGLECHROME, BrowserType.IPAD, BrowserType.IPHONE })
    public void testCacheErrorWithEmptyCache() throws Exception {
        openNoAura("/aura/application.app"); // just need a domain page to set cookie from

        Date expiry = new Date(System.currentTimeMillis() + 60000);
        String cookieName = getManifestCookieName();
        updateCookie(cookieName, "error", expiry, "/");

        List<Request> logs = loadMonitorAndValidateApp(TOKEN, TOKEN, "", TOKEN);
        List<Request> expectedChange = Lists.newArrayList();
        expectedChange.add(new Request("/auraResource", "manifest", 404)); // reset
        expectedChange.add(new Request("/auraResource", "css", 200));
        expectedChange.add(new Request("/auraResource", "js", 200));
        switch (getBrowserType()) {
        case GOOGLECHROME:
            expectedChange.add(new Request(1, getUrl(), null, 200));
            break;
        default:
            expectedChange.add(new Request(getUrl(), null, 200));
        }
        assertRequests(expectedChange, logs);
        assertAppCacheStatus(Status.UNCACHED);
        // There may be a varying number of requests, depending on when the initial manifest response is received.
        Cookie cookie = getDriver().manage().getCookieNamed(cookieName);
        assertNull("No manifest cookie should be present", cookie);
    }

    /**
     * Manifest request limit exceeded for the time period should result in reset. BrowserType.SAFARI is disabled :
     * W-2367702
     */
    @TargetBrowsers({ BrowserType.GOOGLECHROME, BrowserType.IPAD, BrowserType.IPHONE })
    // TODO(W-2701964): Flapping in autobuilds, needs to be revisited
    @Flapper
    public void testManifestRequestLimitExceeded() throws Exception {
        List<Request> logs = loadMonitorAndValidateApp(TOKEN, TOKEN, "", TOKEN);
        assertRequests(getExpectedInitialRequests(), logs);
        assertAppCacheStatus(Status.IDLE);

        Date expiry = new Date(System.currentTimeMillis() + 60000);
        String cookieName = getManifestCookieName();
        Cookie cookie = getDriver().manage().getCookieNamed(cookieName);
        String timeVal = cookie.getValue().split(":")[1];
        updateCookie(cookieName, "8:" + timeVal, expiry, "/");
        logs = loadMonitorAndValidateApp(TOKEN, TOKEN, "", TOKEN);
        List<Request> expectedChange = Lists.newArrayList();

        expectedChange.add(new Request("/auraResource", "manifest", 404)); // reset
        expectedChange.add(new Request(getUrl(), null, 302)); // hard refresh
        switch (getBrowserType()) {
        case GOOGLECHROME:
            expectedChange.add(new Request(3, "/auraResource", "manifest", 200));
            expectedChange.add(new Request(2, getUrl(), null, 200));
            break;
        default:
            expectedChange.add(new Request("/auraResource", "manifest", 200));
            expectedChange.add(new Request(getUrl(), null, 200));
            expectedChange.add(new Request("/auraResource", "css", 200));
            expectedChange.add(new Request("/auraResource", "js", 200));
        }
        assertRequests(expectedChange, logs);
        assertAppCacheStatus(Status.IDLE);
    }

    /**
     * Opening cached app after namespace style change will trigger cache update.
     */
    @ThreadHostileTest("NamespaceDef modification affects namespace")
    @TargetBrowsers({ BrowserType.GOOGLECHROME, BrowserType.SAFARI, BrowserType.IPAD, BrowserType.IPHONE })
    public void testComponentCssChange() throws Exception {
        createDef(StyleDef.class, String.format("%s://%s.%s", DefDescriptor.CSS_PREFIX, namespace, cmpName),
                ".THIS {background-image: url(/auraFW/resources/qa/images/s.gif?@@@TOKEN@@@);}");

        List<Request> logs = loadMonitorAndValidateApp(TOKEN, TOKEN, TOKEN, TOKEN);
        assertRequests(getExpectedInitialRequests(), logs);
        assertAppCacheStatus(Status.IDLE);

        // update a component's css file
        String replacement = getName() + System.currentTimeMillis();

        replaceToken(getTargetComponent().getStyleDescriptor(), replacement);

        logs = loadMonitorAndValidateApp(TOKEN, TOKEN, replacement, TOKEN);
        assertRequests(getExpectedChangeRequests(), logs);
        assertAppCacheStatus(Status.IDLE);

        logs = loadMonitorAndValidateApp(TOKEN, TOKEN, replacement, TOKEN);
        List<Request> expected = Lists.newArrayList(new Request("/auraResource", "manifest", 200));
        assertRequests(expected, logs);
        assertAppCacheStatus(Status.IDLE);
    }

    /**
     * Opening cached app after namespace controller change will trigger cache update.
     */
    @TargetBrowsers({ BrowserType.GOOGLECHROME, BrowserType.SAFARI, BrowserType.IPAD, BrowserType.IPHONE })
    public void testComponentJsChange() throws Exception {
        List<Request> logs = loadMonitorAndValidateApp(TOKEN, TOKEN, "", TOKEN);
        assertRequests(getExpectedInitialRequests(), logs);
        assertAppCacheStatus(Status.IDLE);
        // update a component's js controller file
        String replacement = getName() + System.currentTimeMillis();
        DefDescriptor<?> desc = getTargetComponent().getRemoteControllerDef().getDescriptor();
        replaceToken(desc, replacement);
        logs = loadMonitorAndValidateApp(TOKEN, replacement, "", TOKEN);
        assertRequests(getExpectedChangeRequests(), logs);
        assertAppCacheStatus(Status.IDLE);
        logs = loadMonitorAndValidateApp(TOKEN, replacement, "", TOKEN);
        List<Request> expected = Lists.newArrayList(new Request("/auraResource", "manifest", 200));
        assertRequests(expected, logs);
        assertAppCacheStatus(Status.IDLE);
    }

    /**
     * Opening cached app after component markup change will trigger cache update.
     */
    @TargetBrowsers({ BrowserType.GOOGLECHROME, BrowserType.SAFARI, BrowserType.IPAD, BrowserType.IPHONE })
    public void testComponentMarkupChange() throws Exception {
        List<Request> logs = loadMonitorAndValidateApp(TOKEN, TOKEN, "", TOKEN);
        assertRequests(getExpectedInitialRequests(), logs);
        assertAppCacheStatus(Status.IDLE);
        // update markup of namespaced component used by app
        String replacement = getName() + System.currentTimeMillis();
        replaceToken(getTargetComponent().getDescriptor(), replacement);
        logs = loadMonitorAndValidateApp(replacement, TOKEN, "", TOKEN);
        assertRequests(getExpectedChangeRequests(), logs);
        assertAppCacheStatus(Status.IDLE);
        logs = loadMonitorAndValidateApp(replacement, TOKEN, "", TOKEN);
        List<Request> expected = Lists.newArrayList(new Request("/auraResource", "manifest", 200));
        assertRequests(expected, logs);
        assertAppCacheStatus(Status.IDLE);
    }

    private <T extends Definition> DefDescriptor<T> createDef(Class<T> defClass, String qualifiedName, String content) {
        DefDescriptor<T> desc = Aura.getDefinitionService().getDefDescriptor(qualifiedName, defClass);
        addSourceAutoCleanup(desc, content);
        return desc;
    }

    private String getManifestCookieName() {
        return String.format(COOKIE_NAME, getAuraModeForCurrentBrowser().toString(), namespace, appName);
    }

    private void assertAppCacheStatus(final Status status) {
        auraUITestingUtil.waitUntil(
                new Function<WebDriver, Boolean>() {
                    @Override
                    public Boolean apply(WebDriver input) {
                        return status.name().equals(Status.values()[Integer.parseInt(auraUITestingUtil.getEval(
                                "return window.applicationCache.status;").toString())].name());
                    }
                },
                "applicationCache.status was not " + status.name()
                );
    }

    // provide a test component with TOKENs for replacement to trigger lastMod updates
    private ComponentDef getTargetComponent() throws Exception {
        ContextService service = Aura.getContextService();
        AuraContext context = service.getCurrentContext();
        if (context == null) {
            context = service.startContext(Mode.SELENIUM, Format.HTML, Authentication.AUTHENTICATED);
        }
        return Aura.getDefinitionService().getDefinition(
                String.format("%s:%s", namespace, cmpName), ComponentDef.class);
    }

    /**
     * this function will check each request in actual list against expected list. fudge is the number this request
     * suppose to show up. we remove the request from expected list once it has been visited #fudge times. any missing
     * request will be added to missingRequests list.
     * 
     * @param expected : list of expected request
     * @param actual : list of actual request captured by log
     * @throws Exception
     */
    private void assertRequests(List<Request> expected, List<Request> actual) throws Exception {
        boolean failed;

        List<Request> unexpectedRequests = Lists.newArrayList();
        List<Request> expectedRequests = Lists.newArrayList(expected);
        List<Request> missingRequests = Lists.newArrayList();
        for (Request r : actual) {
            int idx = expectedRequests.indexOf(r);
            if (idx != -1) {
                if (expectedRequests.get(idx).mark()) {
                    expectedRequests.remove(idx);
                }
            } else {
                unexpectedRequests.add(r);
            }
        }
        for (Request r : expectedRequests) {
            if (!r.passed()) {// return fudge > 0 && count > 0;
                missingRequests.add(r);
            }
        }

        failed = unexpectedRequests.size() > 0 || missingRequests.size() > 0;

        if (debug || failed) {
            System.out.println(">>> assertRequests: ");
            System.out.println("EXPECTED:");
            for (Request r : expected) {
                System.out.println("E: " + r + ",fudge:" + r.fudge);
            }
            System.out.println("ACTUAL:");
            for (Request r : actual) {
                r.setShowExtras(failed);
                System.out.println("A: " + r);
            }
        }
        if (failed) {
            StringBuffer sb = new StringBuffer();
            String separator = "";

            if (unexpectedRequests.size() > 0) {
                sb.append("Unexpected requests:\n");
                sb.append(unexpectedRequests);
                separator = "\n";
            }
            if (missingRequests.size() > 0) {
                sb.append(separator);
                sb.append("Missing Requests:\n");
                sb.append(missingRequests);
            }
            fail(sb.toString());
        }
    }

    /**
     * Load and get all the log lines for the app load. Some sanity checks that our simple test app is functional after
     * cache resolutions.
     * <ul>
     * <li>updated markup text is rendered (markupToken)</li>
     * <li>updated client actions functional (jsToken)</li>
     * <li>updated styling applied (cssToken)</li>
     * <li>updated framework called (fwToken)</li>
     * </ul>
     * 
     * @param markupToken The text to be found in the markup.
     * @param jsToken The text to be found from js
     * @param cssToken The text to be found from css.
     * @param Token The text to be found from the framework.
     */
    private List<Request> loadMonitorAndValidateApp(final String markupToken, String jsToken, String cssToken,
            String fwToken) throws Exception {
        TestLoggingAdapterController.beginCapture();

        // Opening a page through WebDriverTestCase adds a nonce to ensure fresh resources. In this case we want to see
        // what's cached, so build our URL and call WebDriver.get() directly.
        String url = getUrl();
        Map<String, String> params = new HashMap<>();
        params.put("aura.mode", getAuraModeForCurrentBrowser().toString());
        url = addUrlParams(url, params);
        getDriver().get(getAbsoluteURI(url).toString());

        auraUITestingUtil.waitUntilWithCallback(
                new Function<WebDriver, Integer>() {
                    @Override
                    public Integer apply(WebDriver input) {
                        Integer appCacheStatus = Integer.parseInt(auraUITestingUtil.getEval(
                                "return window.applicationCache.status;").toString());
                        if (appCacheStatus != 3 && appCacheStatus != 2) {
                            return appCacheStatus;
                        } else {
                            return null;
                        }
                    }
                },
                new ExpectedCondition<String>() {
                    @Override
                    public String apply(WebDriver d) {
                        Object ret = auraUITestingUtil.getRawEval("return window.applicationCache.status");
                        return "Current AppCache status is "
                                + auraUITestingUtil.appCacheStatusIntToString(((Long) ret).intValue());
                    }
                },
                10,
                "fail waiting on application cache not to be Downloading or Checking before clicking on 'clickableme'");

        auraUITestingUtil
                .waitUntil(
                        new Function<WebDriver, WebElement>() {
                            @Override
                            public WebElement apply(WebDriver input) {
                                try {
                                    WebElement find = findDomElement(By
                                            .cssSelector(".clickableme"));
                                    if (markupToken.equals(find.getText())) {
                                        return find;
                                    }
                                } catch (StaleElementReferenceException e) {
                                    // slight chance of happening between the findDomElement and getText
                                }
                                return null;
                            }
                        },
                        "fail to load clickableme"
                );
        Thread.sleep(200);
        List<Request> logs = endMonitoring();

        String output = auraUITestingUtil.waitUntil(
                new Function<WebDriver, String>() {
                    @Override
                    public String apply(WebDriver input) {
                        try {
                            WebElement find = findDomElement(By
                                    .cssSelector(".clickableme"));
                            find.click();
                            WebElement output = findDomElement(By
                                    .cssSelector("div.attroutput"));
                            return output.getText();
                        } catch (StaleElementReferenceException e) {
                            // could happen before the click or if output is
                            // rerendering
                        }
                        return null;
                    }
                }, "fail to click on clickableme or couldn't locate output value");

        assertEquals("Unexpected alert text",
                String.format("%s%s%s", jsToken, cssToken, fwToken), output);

        return logs;
    }

    private String getUrl() {
        return String.format("/%s/%s.app", namespace, appName);
    }

    // replaces TOKEN found in the source file with the provided replacement
    private void replaceToken(DefDescriptor<?> descriptor, String replacement) throws Exception {
        assertNotNull("Missing descriptor for source replacement!", descriptor);
        ContextService service = Aura.getContextService();
        AuraContext context = service.getCurrentContext();
        if (context == null) {
            context = service.startContext(Mode.SELENIUM, Format.HTML,
                    Authentication.AUTHENTICATED);
        }
        Source<?> source = context.getDefRegistry().getSource(descriptor);
        String originalContent = source.getContents();
        assert originalContent.contains(TOKEN);
        source.addOrUpdate(originalContent.replace(TOKEN, replacement));
    }

    private List<Request> endMonitoring() {
        List<Request> logs = Lists.newLinkedList();
        for (Map<String, Object> log : TestLoggingAdapterController.endCapture()) {
            if (!"GET".equals(log.get("requestMethod"))) {
                if (debug) {
                    // Log ignored lines so that we can monitor what happens. The line above had nulls as requestMethod,
                    // so this catches randomness.
                    System.out.println("IGNORED: " + log);
                }
                continue;
            }
            int status = -1;

            if (log.get("httpStatus") != null) {
                try {
                    status = Integer.parseInt((String) log.get("httpStatus"));
                } catch (NumberFormatException nfe) {
                }
            }
            Request toAdd = new Request(log.get("auraRequestURI").toString(), null, status);
            for (String part : AuraTextUtil.urldecode(log.get("auraRequestQuery").toString()).split("&")) {
                String[] parts = part.split("=", 2);
                String key = parts[0].substring(AURA.length() + 1);
                String v = parts[1];
                toAdd.put(key, (v != null && !v.isEmpty()) ? v : null);
            }
            logs.add(toAdd);
        }
        return logs;
    }

    /**
     * Get the set of expected requests on change. These are the requests that we expect for filling the app cache.
     * 
     * @return the list of request objects, not necessarily in order.
     */
    private List<Request> getExpectedChangeRequests() {
        switch (getBrowserType()) {
        case GOOGLECHROME:
            /*
             * For Chrome Get the set of expected requests on change. These are the requests that we expect for filling
             * the app cache. The explanation is as follows. <ul> <li>The manifest is pulled</li> <li>The browser now
             * gets all three components, initial, css, and js</li> <li>Finally, the browser re-fetches the manifest to
             * check contents</li> <ul> The primary difference between this and the initial requests is that we don't
             * get the initial page twice, and we get the manifest three times... odd that. we usually only get js and
             * css only once, but it's not stable, do see some test get them twice sometimes.
             */
            return ImmutableList.of(
                    // The manifest change causes the correct fetch, eliminating our 302 and 404
                    // new Request(getUrl(), null, 302), // hard refresh
                    // new Request("/auraResource", "manifest", 404), // manifest out of date
                    new Request(3, "/auraResource", "manifest", 200),
                    new Request(2, getUrl(), null, 200), // rest are cache updates
                    new Request(2, "/auraResource", "css", 200),
                    new Request(2, "/auraResource", "js", 200));
        default:
            /*
             * For iOS Get the set of expected requests on change. These are the requests that we expect for filling the
             * app cache. The explanation is as follows. <ul> <li>The manifest is pulled</li> <li>The browser now gets
             * all three components, initial, css, and js</li> <li>Finally, the browser re-fetches the manifest to check
             * contents</li> <ul> The primary difference between this and the initial requests is that we get the
             * initial page twice
             */
            return ImmutableList.of(
                    // The manifest change causes the correct fetch, eliminating our 302
                    // new Request(getUrl(), null, 302), // hard refresh
                    // new Request("/auraResource", "manifest", 404), // manifest out of date
                    new Request("/auraResource", "manifest", 200),
                    new Request(2, getUrl(), null, 200), // rest are cache updates
                    new Request(2, "/auraResource", "css", 200),
                    new Request(2, "/auraResource", "js", 200));
        }
    }

    /**
     * Get the set of expected requests on change. These are the requests that we expect for filling the app cache.
     * 
     * @return the list of request objects, not necessarily in order.
     */
    private List<Request> getExpectedInitialRequests() {
        switch (getBrowserType()) {
        case GOOGLECHROME:
            /*
             * For Chrome Get the set of expected initial requests. These are the requests that we expect for filling
             * the app cache. The explanation is as follows. <ul> <li>The browser requests the initial page from the
             * server</li> <li>The manifest is pulled</li> <li>The browser now gets all three components, initial, css,
             * and js</li> <li>Finally, the browser re-fetches the manifest to check contents</li> <ul> Note that there
             * are two requests for the initial page, one as the first request, and one to fill the app cache (odd, but
             * true). There are also two manifest requests.
             */
            return ImmutableList.of(new Request(2, getUrl(), null, 200),
                    new Request(2, "/auraResource", "manifest", 200),
                    new Request(2, "/auraResource", "css", 200),
                    new Request(2, "/auraResource", "js", 200));
        default:
            /*
             * For iOS Get the set of expected requests on change. These are the requests that we expect for filling the
             * app cache. The explanation is as follows. <ul> <li>The manifest is pulled</li> <li>The browser now gets
             * all three components, initial, css, and js</li> <li>Finally, the browser re-fetches the manifest to check
             * contents</li> <ul> Note that there are also two css and js request.
             */
            return ImmutableList.of(new Request(1, getUrl(), null, 200),
                    new Request(1, "/auraResource", "manifest", 200),
                    new Request(2, "/auraResource", "css", 200),
                    new Request(2, "/auraResource", "js", 200));
        }
    }

    /**
     * A request object, which can either be an 'expected' request, or an 'actual' request. Expected requests can also
     * have a fudge factor allowing multiple requests for the resource. This is very helpful for different browsers
     * doing diferent things with the manifest. We allow multiple fetches of both the manifest and initial page in both
     * the initial request and the requests on change of resource.
     */
    static class Request extends HashMap<String, String> {
        private static final long serialVersionUID = 4149738936658714181L;
        private static final ImmutableSet<String> validKeys = ImmutableSet.of("URI", "format", "httpStatus");

        private final int fudge;
        private int count = 0;
        private Map<String, String> extras = null;
        private boolean showExtras = false;

        Request(int fudge, String URI, String format, int status) {
            super();
            this.fudge = fudge;
            put("URI", URI);
            put("format", format);
            if (status != -1) {
                put("httpStatus", String.valueOf(status));
            }
        }

        Request(String URI, String format, int status) {
            super();
            this.fudge = 0;
            put("URI", URI);
            put("format", format);
            if (status != -1) {
                put("httpStatus", String.valueOf(status));
            }
        }

        @Override
        public String put(String k, String v) {
            if (validKeys.contains(k)) {
                return super.put(k, v);
            } else {
                if (extras == null) {
                    extras = new HashMap<>();
                }
                extras.put(k, v);
            }
            return null;
        }

        /**
         * We passed the test for this request.
         * 
         * @return true if we got the request. each request from expected list must show up at least once in the actual
         *         list.
         */
        public boolean passed() {
            return fudge > 0 && count > 0;
        }

        /**
         * Mark the request as found.
         * 
         * @return true if it should be removed.count > fudge: browsers don't behave consistently. better have a loose
         *         bound here. we are comparing two requests list: actual list and expected list. count start at 0, we
         *         are expecting 1,2,..,fudge, or fudge+1 request. once we have some request X that show up fudge+1 in
         *         actual list, X get removed from expected list. then if we receive another X again, it will be added
         *         to unexpected requestes list and error out.
         */
        public boolean mark() {
            if (fudge == 0) {
                return true;
            } else {
                count += 1;
                return count > fudge;
            }
        }

        public void setShowExtras(boolean value) {
            this.showExtras = value;
        }

        @Override
        public String toString() {
            if (extras == null || !showExtras) {
                return super.toString();
            } else {
                return super.toString() + String.valueOf(extras);
            }
        }
    }

    private void updateCookie(String name, String value, Date expiry, String path) {
        SimpleDateFormat sd = new SimpleDateFormat();
        sd.setTimeZone(TimeZone.getTimeZone("GMT"));
        String expiryFormatted = sd.format(expiry);
        String command = "document.cookie = '" + name + "=" + value + "; expires=" + expiryFormatted + "; path=" + path
                + "';";
        auraUITestingUtil.getEval(command);
    }
}

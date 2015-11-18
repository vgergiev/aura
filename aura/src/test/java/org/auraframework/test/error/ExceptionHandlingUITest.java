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
package org.auraframework.test.error;

import org.auraframework.Aura;
import org.auraframework.controller.java.ServletConfigController;
import org.auraframework.def.ApplicationDef;
import org.auraframework.def.ComponentDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.InterfaceDef;
import org.auraframework.system.AuraContext.Authentication;
import org.auraframework.system.AuraContext.Format;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.test.util.WebDriverTestCase;
import org.auraframework.util.test.annotation.ThreadHostileTest;
import org.auraframework.util.test.annotation.UnAdaptableTest;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.common.base.Function;

/**
 * What should you see when something goes wrong. {@link ThreadHostile} due to setProdConfig and friends.
 */
@UnAdaptableTest
public class ExceptionHandlingUITest extends WebDriverTestCase {
    public ExceptionHandlingUITest(String name) {
        super(name);
    }

    private static final String baseAppTag = "<aura:application access='GLOBAL' %s>%s</aura:application>";

    private static final String errorBoxPath = "//div[@class='auraMsgMask auraForcedErrorBox']//div[@id='auraErrorMessage']";

    private void setProdConfig() throws Exception {
        ServletConfigController.setProductionConfig(true);
        Aura.getContextService().endContext();
        Aura.getContextService().startContext(Mode.DEV, Format.HTML, Authentication.AUTHENTICATED);
    }

    private void setProdContextWithoutConfig() throws Exception {
        Aura.getContextService().endContext();
        Aura.getContextService().startContext(Mode.PROD, Format.HTML, Authentication.AUTHENTICATED);
    }

    private void setDevContextWithoutConfig() throws Exception {
        Aura.getContextService().endContext();
        Aura.getContextService().startContext(Mode.DEV, Format.HTML, Authentication.AUTHENTICATED);
    }

    private String getAppUrl(String attributeMarkup, String bodyMarkup) throws Exception {
        String appMarkup = String.format(baseAppTag, attributeMarkup, bodyMarkup);
        DefDescriptor<ApplicationDef> add = addSourceAutoCleanup(ApplicationDef.class, appMarkup);
        return String.format("/%s/%s.app", add.getNamespace(), add.getName());
    }

    /**
     * Due to duplicate div#auraErrorMessage on exceptions from server rendering, use different CSS selector to check
     * exception message. W-1308475 - Never'd removal/change of duplicate div#auraErrorMessage
     */
    private void assertNoStacktraceServerRendering() throws Exception {
        WebElement elem = findDomElement(By.xpath(errorBoxPath));
        if (elem == null) {
            fail("error message not found");
        }
        String actual = elem.getText().replaceAll("\\s+", " ");
        assertEquals("Unable to process your request", actual);
    }

    private void assertNoStacktrace() throws Exception {
        String actual = auraUITestingUtil.getAuraErrorMessage().replaceAll("\\s+", " ");
        assertEquals("Unable to process your request", actual);
    }

    /**
     * Due to duplicate div#auraErrorMessage on exceptions from server rendering, use different CSS selector to check
     * exception message. W-1308475 - Never'd removal/change of duplicate div#auraErrorMessage
     */
    private void assertStacktraceServerRendering(String messageStartsWith, String... causeStartsWith) throws Exception {
        WebElement elem = findDomElement(By.xpath(errorBoxPath));
        if (elem == null) {
            fail("error message not found");
        }
        String actual = elem.getText().replaceAll("\\s+", " ");
        assertStacktraceCommon(actual, messageStartsWith, causeStartsWith);
    }

    private void assertStacktrace(String messageStartsWith, String... causeStartsWith) throws Exception {
        String actual = auraUITestingUtil.getAuraErrorMessage().replaceAll("\\s+", " ");
        assertStacktraceCommon(actual, messageStartsWith, causeStartsWith);
    }

    private void assertStacktraceCommon(String actual, String messageStartsWith, String... causeStartsWith)
            throws Exception {
        if (!actual.contains(messageStartsWith)) {
            fail("unexpected error message - expected <" + messageStartsWith + "> but got <" + actual + ">");
        }
        for (String expectedCause : causeStartsWith) {
            if (!actual.contains(expectedCause)) {
                fail("unexpected cause - expected <" + expectedCause + "> but got <" + actual + ">");
            }
        }
    }

    /**
     * Generic error message displayed in PRODUCTION if component provider instantiation throws.
     */
    @ThreadHostileTest("PRODUCTION")
    public void testProdCmpProviderThrowsDuringInstantiation() throws Exception {
        setProdConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(
                InterfaceDef.class,
                "<aura:interface provider='java://org.auraframework.impl.java.provider.TestProviderThrowsDuringInstantiation'></aura:interface>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertNoStacktrace();
    }

    /**
     * Error displayed if provider throws during instantiation.
     */
    public void testCmpProviderThrowsDuringInstantiation() throws Exception {
        setDevContextWithoutConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(
                InterfaceDef.class,
                "<aura:interface provider='java://org.auraframework.impl.java.provider.TestProviderThrowsDuringInstantiation'></aura:interface>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertStacktrace(
                "java.lang.RuntimeException: that was intentional at org.auraframework.impl.java.provider.TestProviderThrowsDuringInstantiation.",
                "(TestProviderThrowsDuringInstantiation.java:");
    }

    /**
     * Generic error message displayed in PRODUCTION if application provider instantiation throws.
     */
    @ThreadHostileTest("PRODUCTION")
    public void testProdAppProviderThrowsDuringInstantiation() throws Exception {
        setProdConfig();
        openRaw(getAppUrl(
                "provider='java://org.auraframework.impl.java.provider.TestProviderThrowsDuringInstantiation'", ""));
        assertNoStacktrace();
    }

    /**
     * Error displayed if provider throws during instantiation.
     */
    public void testAppProviderThrowsDuringInstantiation() throws Exception {
        setDevContextWithoutConfig();
        openRaw(getAppUrl(
                "provider='java://org.auraframework.impl.java.provider.TestProviderThrowsDuringInstantiation'", ""));
        assertStacktrace("that was intentional at org.auraframework.impl.java.provider.TestProviderThrowsDuringInstantiation.");
    }

    /**
     * Generic error message displayed in PRODUCTION if component provider instantiation throws.
     */
    @ThreadHostileTest("PRODUCTION")
    public void testProdCmpProviderThrowsDuringProvide() throws Exception {
        setProdConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(
                InterfaceDef.class,
                "<aura:interface provider='java://org.auraframework.impl.java.provider.TestProviderThrowsDuringProvide'></aura:interface>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertNoStacktrace();
    }

    /**
     * Stacktrace displayed in non-PRODUCTION if component provider instantiation throws.
     */
    public void testCmpProviderThrowsDuringProvide() throws Exception {
        setDevContextWithoutConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(
                InterfaceDef.class,
                "<aura:interface provider='java://org.auraframework.impl.java.provider.TestProviderThrowsDuringProvide'></aura:interface>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertStacktrace("java.lang.RuntimeException: out of stock at .", cdd.toString());
    }

    /**
     * Generic error message displayed in PRODUCTION if component model instantiation throws.
     */
    @ThreadHostileTest("PRODUCTION")
    public void testProdCmpModelThrowsDuringInstantiation() throws Exception {
        setProdConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component model='java://org.auraframework.components.test.java.model.TestModelThrowsDuringInstantiation'></aura:component>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertNoStacktrace();
    }

    /**
     * Stacktrace displayed in non-PRODUCTION if component model instantiation throws.
     */
    public void testCmpModelThrowsDuringInstantiation() throws Exception {
        setDevContextWithoutConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component model='java://org.auraframework.components.test.java.model.TestModelThrowsDuringInstantiation'></aura:component>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertStacktrace(
                "java.lang.RuntimeException: surprise! at org.auraframework.components.test.java.model.TestModelThrowsDuringInstantiation.",
                "(TestModelThrowsDuringInstantiation.java:");
    }

    /**
     * Generic error message displayed in PRODUCTION if component renderer instantiation throws.
     */
    @ThreadHostileTest("PRODUCTION")
    public void testProdCmpRendererThrowsDuringInstantiation() throws Exception {
        setProdConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(
                ComponentDef.class,
                "<aura:component renderer='java://org.auraframework.impl.renderer.sampleJavaRenderers.TestRendererThrowsDuringInstantiation'></aura:component>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertNoStacktrace();
    }

    /**
     * Stacktrace displayed in non-PRODUCTION if component renderer instantiation throws.
     */
    public void testCmpRendererThrowsDuringInstantiation() throws Exception {
        setDevContextWithoutConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(
                ComponentDef.class,
                "<aura:component renderer='java://org.auraframework.impl.renderer.sampleJavaRenderers.TestRendererThrowsDuringInstantiation'></aura:component>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertStacktrace(
                "java.lang.RuntimeException: invisible me at org.auraframework.impl.renderer.sampleJavaRenderers.TestRendererThrowsDuringInstantiation.",
                "(TestRendererThrowsDuringInstantiation.java:");
    }

    /**
     * Generic error message displayed in PRODUCTION if component renderer throws.
     */
    @ThreadHostileTest("PRODUCTION")
    public void testProdCmpRendererThrowsDuringRender() throws Exception {
        setProdConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(
                ComponentDef.class,
                "<aura:component renderer='java://org.auraframework.impl.renderer.sampleJavaRenderers.TestRendererThrowingException'></aura:component>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertNoStacktraceServerRendering();
    }

    /**
     * Stacktrace displayed in non-PRODUCTION if component renderer throws.
     */
    public void testCmpRendererThrowsDuringRender() throws Exception {
        setProdContextWithoutConfig();
        DefDescriptor<?> cdd = addSourceAutoCleanup(
                ComponentDef.class,
                "<aura:component renderer='java://org.auraframework.impl.renderer.sampleJavaRenderers.TestRendererThrowingException'></aura:component>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        assertStacktraceServerRendering("org.auraframework.throwable.AuraExecutionException: org.auraframework."
                + "renderer.ComponentRenderer: org.auraframework.throwable.AuraExecutionException: org.auraframework."
                + "renderer.HtmlRenderer: org.auraframework.throwable.AuraExecutionException: org.auraframework."
                + "renderer.HtmlRenderer: org.auraframework.throwable.AuraExecutionException: org.auraframework."
                + "renderer.ExpressionRenderer: org.auraframework.throwable.AuraExecutionException: org.auraframework."
                + "renderer.ComponentRenderer: org.auraframework.throwable.AuraExecutionException: org.auraframework."
                + "impl.renderer.sampleJavaRenderers.TestRendererThrowingException: java.lang.ArithmeticException: From TestRendererThrowingException at");
    }

    /**
     * Parse error stack trace for application includes filename along with row,col
     */
    public void testAppThrowsWithFileName() throws Exception {
        setProdContextWithoutConfig();
        // load the definition in the loader
        DefDescriptor<?> add = addSourceAutoCleanup(
                ApplicationDef.class,
                "<aura:application '></aura:application>");
        openRaw(String.format("/%s/%s.app", add.getNamespace(), add.getName()));
        // Verifying common bits of parser (sjsxp vs woodstox) error
        assertStacktrace("org.auraframework.throwable.AuraUnhandledException:");
        assertStacktrace(String.format("markup://%s:%s:", add.getNamespace(), add.getName()));
        assertStacktrace("[2,19]");
    }

    /**
     * Parse error stack trace for controller includes filename
     */
    public void testControllerThrowsWithFileName() throws Exception {
        String fileName = "auratest/parseError";
        openRaw(fileName + ".cmp");
        assertStacktrace("org.auraframework.throwable.AuraRuntimeException: ");
        assertStacktrace("auratest/parseError/parseErrorController.js");
    }

    /**
     * Default handler for ClientOutOfSync will reload the page.
     */
    public void testClientOutOfSyncDefaultHandler() throws Exception {
        open("/updateTest/updateWithoutHandling.cmp?text=initial");

        // make a client-side change to the page
        findDomElement(By.cssSelector(".update")).click();
        waitForElementText(findDomElement(By.cssSelector(".uiOutputText")), "modified", true, 3000);
        assertTrue("Page was not changed after client action",
                auraUITestingUtil.getBooleanEval("return !!document.__PageModifiedTestFlag"));

        // make server POST call with outdated lastmod
        findDomElement(By.cssSelector(".trigger")).click();

        // Wait till prior prior client-side change is gone indicating page
        // reload
        WebDriverWait wait = new WebDriverWait(getDriver(), 30);
        wait.until(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver d) {
                return !auraUITestingUtil.getBooleanEval("return !!document.__PageModifiedTestFlag");
            }
        });
        // Wait for page to reload and aura framework initialization
        auraUITestingUtil.waitForAuraInit();
        waitForElementText(findDomElement(By.cssSelector(".uiOutputText")), "initial", true, 3000);
    }

    /**
     * Test XSS handling
     */
    public void testXssScenarioOne() throws Exception {
        DefDescriptor<?> cdd = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component>{!'&lt;'}script{!'&gt;'}alert('foo');{!'&lt;'}/script{!'&gt;'}</aura:component>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        auraUITestingUtil.waitForElementFunction(By.tagName("body"), new Function<WebElement, Boolean>() {
                @Override
			    public Boolean apply(WebElement elem) {
				    return elem.getText().contains("alert(");
			    }
            },
            "XSS may have injected a bad <script> tag");
        assertEquals("", auraUITestingUtil.getAuraErrorMessage());
    }

    public void testXssScenarioTwo() throws Exception {
        DefDescriptor<?>  cdd = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component>{!'&lt;script&gt;'}alert({!'\"foo\");&lt;/script&gt;'}</aura:component>");
        openRaw(getAppUrl("", String.format("<%s:%s/>", cdd.getNamespace(), cdd.getName())));
        auraUITestingUtil.waitForElementFunction(By.tagName("body"), new Function<WebElement, Boolean>() {
                @Override
		        public Boolean apply(WebElement elem) {
			        return elem.getText().contains("alert(");
		        }
            },
            "XSS may have injected a bad <script> tag");
        assertEquals("", auraUITestingUtil.getAuraErrorMessage());
    }
}

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

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

import org.auraframework.system.AuraContext.Mode;
import org.auraframework.test.util.WebDriverTestCase.TargetBrowsers;
import org.auraframework.test.util.WebDriverUtil.BrowserType;
import org.openqa.selenium.By;

/**
 * ComponentClassErrorTest is mainly for verifying error message when we error out from component's helper/renderer
 * The tests check if the content of error message contains the error location. Different browser has different content
 * for traceback. This is only for debugging, so only running the tests for Chrome for now.
 */
@TargetBrowsers({ BrowserType.GOOGLECHROME })
public class ComponentClassErrorUITest extends AbstractErrorUITestCase {

    public ComponentClassErrorUITest(String name) {
        super(name);
    }

    /* Test with dynamic created component */
    // create componentClassParent.cmp in controller dynamically, expect it to error out from render
    public void testDynamicCreatedCmpErrorOutFromRender() throws Exception {
        String expectLocation = "auratest$componentClassParent.renderer.render";
        String expectContainedMessage = "blahFromParentRerender is not defined";

        String url = "/auratest/componentClassLifecycleTest.cmp?" +
                "componentDef=markup://auratest:componentClassParent&" +
                "errorOutFrom=errorOutFromRender_Parent";
        open(url, Mode.DEV);
        findDomElement(By.cssSelector(".uiButton_createCmp")).click();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    /* Tests with parent component */
    // load componentClassParent.cmp, error out from its re-render
    public void testParentErrorOutFromReRender() throws Exception {
        String expectLocation = "auratest$componentClassParent.renderer.rerender";
        String expectContainedMessage = "blahFromParentReRerender is not defined";

        String url = "/auratest/componentClassParent.cmp?errorOutFromReRender_Parent=true";
        open(url, Mode.DEV);
        findDomElement(By.cssSelector(".uiButtonParent")).click();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassParent.cmp, error out from its after-render
    public void testParentErrorOutFromAfterRender() throws Exception {
        String expectLocation = "auratest$componentClassParent.renderer.afterRender";
        String expectContainedMessage = "blahFromParentAfterRerender is not defined";

        String url = "/auratest/componentClassParent.cmp?errorOutFromAfterRender_Parent=true";
        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    /* Tests with grandChild component */
    // load componentClassGrandChildClientProvider.cmp, error out from componentClassParent.cmp's render
    public void testGrandChildClientErrorOutFromParentRender() throws Exception {
        String expectLocation = "auratest$componentClassParent.renderer.render";
        String expectContainedMessage = "blahFromParentRerender is not defined";
        String url = "/auratest/componentClassGrandChildClientProvider.cmp?errorOutFromRender_Parent=true";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassGrandChildClientProvider.cmp, error out from componentClassChild.cmp's render
    public void testGrandChildClientErrorOutFromChildRender() throws Exception {
        String expectLocation = "auratest$componentClassGrandChildClientProvider.renderer.render";
        String expectContainedMessage = "blahFromChildRerender is not defined";
        String url = "/auratest/componentClassGrandChildClientProvider.cmp?errorOutFromRender_Child=true";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassGrandChildClientProvider.cmp, error out from componentClassParent.cmp's helper
    public void testGrandChildClientErrorOutFromParentHelper() throws Exception {
        String expectLocation = "auratest$componentClassParent.helper.getDelimiter";
        String expectContainedMessage = "blahFromParentHelper is not defined";
        String url = "/auratest/componentClassGrandChildClientProvider.cmp?errorOutFromHelper_Parent=true";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassGrandChildClientProvider.cmp, error out from componentClassParent.cmp's helper
    public void testGrandChildClientErrorOutFromChildHelper() throws Exception {
        String expectLocation = "auratest$componentClassChild.helper.getDelimiter";
        String expectContainedMessage = "blahFromChildHelper is not defined";
        String url = "/auratest/componentClassGrandChildClientProvider.cmp?errorOutFromHelper_Child=true";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    /* Test with client provided component */
    // load componentClassGrandChildServerProvider through componentClassClientProvider.cmp, error out from
    // componentClassChild's helper
    public void testClientProvidedGrandChildClientErrorOutFromChildHelper() throws Exception {
        String expectLocation = "auratest$componentClassChild.helper.getDelimiter";
        String expectContainedMessage = "blahFromChildHelper is not defined";

        String url = "/auratest/componentClassClientProvider.cmp?" +
                "requestDescriptor=auratest:componentClassGrandChildServerProvider&" +
                "requestAttributes={'errorOutFromHelper_Child':true}";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassGrandChildServerProvider through componentClassClientProvider.cmp, error out from
    // componentClassParent's helper
    public void testClientProvidedGrandChildClientErrorOutFromParentHelper() throws Exception {
        String expectLocation = "auratest$componentClassParent.helper.getDelimiter";
        String expectContainedMessage = "blahFromParentHelper is not defined";
        String url = "/auratest/componentClassClientProvider.cmp?" +
                "requestDescriptor=auratest:componentClassGrandChildServerProvider&" +
                "requestAttributes={'errorOutFromHelper_Parent':true}";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassGrandChildServerProvider through componentClassClientProvider.cmp, error out from
    // componentClassChild's render
    public void testClientProvidedGrandChildClientErrorOutFromChildRender() throws Exception {
        String expectLocation = "auratest$componentClassChild.renderer.render";
        String expectContainedMessage = "blahFromChildRerender is not defined";

        String url = "/auratest/componentClassClientProvider.cmp?" +
                "requestDescriptor=auratest:componentClassGrandChildServerProvider&" +
                "requestAttributes={'errorOutFromRender_Child':true}";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassGrandChildServerProvider through componentClassClientProvider.cmp, error out from
    // componentClassParent's render
    public void testClientProvidedGrandChildClientErrorOutFromParentRender() throws Exception {
        String expectLocation = "auratest$componentClassParent.renderer.render";
        String expectContainedMessage = "blahFromParentRerender is not defined";
        String url = "/auratest/componentClassClientProvider.cmp?" +
                "requestDescriptor=auratest:componentClassGrandChildServerProvider&" +
                "requestAttributes={'errorOutFromRender_Parent':true}";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    /* Tests with server provided component */
    // load componentClassGrandChildServerProvider through componentClassClientProvider.cmp, error out from
    // componentClassChild's helper
    public void testServerProvidedGrandChildClientErrorOutFromChildHelper() throws Exception {
        String expectLocation = "auratest$componentClassChild.helper.getDelimiter";
        String expectContainedMessage = "blahFromChildHelper is not defined";
        String url ="/auratest/componentClassServerProvider.cmp?" +
                "requestDescriptor=auratest:componentClassGrandChildServerProvider&" +
                "requestAttributes={'errorOutFromHelper_Child':true}";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassGrandChildServerProvider through componentClassClientProvider.cmp, error out from
    // componentClassParent's helper
    public void testServerProvidedGrandChildClientErrorOutFromParentHelper() throws Exception {
        String expectLocation = "auratest$componentClassParent.helper.getDelimiter";
        String expectContainedMessage = "blahFromParentHelper is not defined";
        String url = "/auratest/componentClassServerProvider.cmp?" +
                "requestDescriptor=auratest:componentClassGrandChildServerProvider&" +
                "requestAttributes={'errorOutFromHelper_Parent':true}";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassGrandChildServerProvider through componentClassClientProvider.cmp, error out from
    // componentClassChild's render
    public void testServerProvidedGrandChildClientErrorOutFromChildRender() throws Exception {
        String expectLocation = "auratest$componentClassChild.renderer.render";
        String expectContainedMessage = "blahFromChildRerender is not defined";
        String url = "/auratest/componentClassServerProvider.cmp?" +
                "requestDescriptor=auratest:componentClassGrandChildServerProvider&" +
                "requestAttributes={'errorOutFromRender_Child':true}";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

    // load componentClassGrandChildServerProvider through componentClassServerProvider.cmp, error out from
    // componentClassParent's render
    public void testServerProvidedGrandChildClientErrorOutFromParentRender() throws Exception {
        String expectLocation = "auratest$componentClassParent.renderer.render";
        String expectContainedMessage = "blahFromParentRerender is not defined";
        String url = "/auratest/componentClassServerProvider.cmp?" +
                "requestDescriptor=auratest:componentClassGrandChildServerProvider&" +
                "requestAttributes={'errorOutFromRender_Parent':true}";

        open(url, Mode.DEV, false);
        auraUITestingUtil.waitForDocumentReady();

        String actualMessage = findErrorMessage();
        assertThat("Did not find expected error in error message element.", actualMessage, containsString(expectContainedMessage));
        assertThat("We expect error comes from " + expectLocation, actualMessage, containsString(expectLocation));
    }

}

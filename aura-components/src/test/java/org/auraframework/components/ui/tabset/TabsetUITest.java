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
package org.auraframework.components.ui.tabset;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;

import org.auraframework.test.util.WebDriverTestCase;
import org.auraframework.test.util.WebDriverUtil.BrowserType;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TabsetUITest extends WebDriverTestCase {
    private final String URL = "/uitest/tabset_Test.cmp";
    private final By ACTIVE_LI_LOCATOR = By.cssSelector("li[class*='tabs__item active uiTabItem'] > a");
    private final By ACTIVE_SECTION = By.cssSelector("section[class*='tabs__content active uiTab']");
    private final String[] TITLE_ARRAY = { "Accounts", "Contacts", "Opportunities", "Leads", "Chatter", "Icon",
            "Dashboards" };
    private final String[] BODY_ARRAY = { "tab 1 contents", "tab 2 contents", "tab 3 contents", "tab 4 contents",
            "tab 5 contents", "tab 6 contents", "tab 7 contents", };
    private int NUMBER_OF_TABS = 7;

    public TabsetUITest(String name) {
        super(name);
    }

    /********************************************** HELPER FUNCTIONS ******************************************/

    /**
     * Function that will iterate through all tabs and make sure that we go in the correct order
     * 
     * @param rightOrDownArrow - Key to press
     * @param leftOrUpArrow Key to press
     */
    private void iterateThroughTabs(CharSequence rightOrDownArrow, CharSequence leftOrUpArrow) {
        WebElement element = findDomElement(By.linkText("Accounts"));
        element.click();
        waitForTabSelected("Did not switch over to Accounts tab", element);
        
        WebElement activeSection = findDomElement(ACTIVE_SECTION);

        // Loop through all of the tabs to make sure we get to the correct values
        for (int i = 0; i < TITLE_ARRAY.length; i++) {
            // Verify on correct tab
            assertEquals("Did not get to the correct tab", TITLE_ARRAY[i], element.getText());

            // Verify Section id and tab id match
            assertEquals("The aria-controls id and section id do not match", element.getAttribute("aria-controls"),
                    activeSection.getAttribute("id"));

            // Verify Body text matches what we think it should be
            assertTrue("The body of the section is not what it should be",
                    activeSection.getText().contains(BODY_ARRAY[i]));

            // Go to the next element then grab the new active elements
            element.sendKeys(rightOrDownArrow);
            element = findDomElement(ACTIVE_LI_LOCATOR);
            activeSection = findDomElement(ACTIVE_SECTION);
        }

        // Loop through all of the tabs to make sure we get to the correct values
        for (int i = TITLE_ARRAY.length - 1; i >= 0; i--) {
            element.sendKeys(leftOrUpArrow);
            element = findDomElement(ACTIVE_LI_LOCATOR);
            activeSection = findDomElement(ACTIVE_SECTION);

            // Verify on correct tab
            assertEquals("Did not get to the correct tab", TITLE_ARRAY[i], element.getText());

            // Verify Section id and tab id match
            assertEquals("The aria-controls id and section id do not match", element.getAttribute("aria-controls"),
                    activeSection.getAttribute("id"));

            // Verify Body text matches what we think it should be
            assertTrue("The body of the section is not what it should be",
                    activeSection.getText().contains(BODY_ARRAY[i]));
        }
    }
    
    private void waitForTabSelected(String msg, final WebElement element) {
        WebDriverWait wait = new WebDriverWait(getDriver(), auraUITestingUtil.getTimeout());
        wait.withMessage(msg);
        wait.until(new ExpectedCondition<Boolean>() {
            @Override
            public Boolean apply(WebDriver d) {
            	WebElement parent = element.findElement(By.xpath(".."));
                return parent.getAttribute("class").contains("active");
            }
        });
    }

    /**
     * Verify that when a tab closes that it actually closes
     * 
     * @param loc - string location of tab to close
     */
    private void closeTabAndVerify(String loc) {
        findDomElement(By.xpath(loc)).click();
        int numberOfElement = findDomElements(By.xpath("//ul/li")).size();

        // Subtracting total number of tabs that we expect by 1
        --NUMBER_OF_TABS;
        assertEquals("The number of tabs, after deleting a tab, do not match", numberOfElement, NUMBER_OF_TABS);
    }

    /**
     * Making sure that when an element closes the element that is focused is no longer focused, and focus is on the
     * next item
     * 
     * @param initialFocusLocator - where the current focus is
     * @param initialText - the text that we are expecting
     * @param secondaryText - text of the element where we want the focus to be
     */
    private void checkFocusMoves(String initialFocusLocator, String initialText, String secondaryText) {
        WebElement element = findDomElement(By.xpath(initialFocusLocator));
        assertTrue("Correct element (" + initialText + ") was not found", element.getText().contains(initialText));
        element.click();

        // Close element directly before/after tab current tab
        closeTabAndVerify(initialFocusLocator + "/a");

        // Verify that there is only one active element on the page
        List<WebElement> elms = findDomElements(ACTIVE_LI_LOCATOR);
        assertEquals("Amount of active elements on the page is incorrect", 1, elms.size());

        element = elms.get(0);
        assertTrue("Correct element (" + secondaryText + ") was not found", element.getText().contains(secondaryText));
    }

    /**
     * method to create an xpath location that we are looking for
     * 
     * @param pos - position of the li, under the UL that we are looking for
     * @return - the xpath string
     */
    private String createXPath(int pos) {
        return "//ul/li[" + pos + "]/a";
    }

    /**
     * Function that will create a tab, tab and verify its contents
     * 
     * @param tabName - Name of the tab
     * @param tabBody - Body of the tab
     */
    private void createNewTab(String tabName, String tabBody) {
        WebElement element = findDomElement(By.xpath("//button[contains(@class,'addTab')]"));
        element.click();

        element = findDomElement(ACTIVE_LI_LOCATOR);
        assertTrue("Correct element was not found", element.getText().contains(tabName));

        // Verify Body text matches what we think it should be
        element = findDomElement(ACTIVE_SECTION);
        assertEquals("The body of the section is not what it should be", tabBody, element.getText());
        NUMBER_OF_TABS++;
    }

    /**
     * Function that will create a url based on the item attribute. Depending on what ITem is the page will render
     * differently
     * 
     * @param item - what item we want rendered
     * @return - the desired URL string
     */

    private String createURL(String item, String closable) {
        return URL + "?renderItem=" + item + "&closable=" + closable;
    }

    /**
     * Function verifying that the element we are expecting to be active is actually active
     * 
     * @param loc - the locator for the element
     */

    private void verifyElementIsActive(By loc) {
        WebElement el = findDomElement(loc);
        assertTrue("The Active class name was not found in the non deleted element",
                el.getAttribute("class").contains("active"));
    }

    public void verifyElementFocus(String itemToVerifyAgainst) {
        // Verify correct element is focused (verified with with the class that we are expecting the element to contain)
        String activeElementClass = (String) auraUITestingUtil
                .getEval("return $A.test.getActiveElement().getAttribute('class')");
        assertTrue("Focus is not on ther correct element", activeElementClass.contains(itemToVerifyAgainst));

    }

    /********************************************************************************************************************/

    /**
     * Test that will verify that the arrows keys work. This is not something that will be run on mobile devices
     * 
     * IE7/8 don't handle arrows well. Disabling tests until bug is fixed: W-2295362
     * 
     * 
     * 
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    @ExcludeBrowsers({ BrowserType.ANDROID_PHONE, BrowserType.ANDROID_TABLET, BrowserType.IPHONE, BrowserType.IPAD,
            BrowserType.IE8, BrowserType.IE7 })
    public void testLeftRightUpDownArrows() throws MalformedURLException, URISyntaxException {
        open(createURL("basic", "false"));

        // Left/Up and Right/Down Arrows do the samething. Making sure that the result is also the same
        iterateThroughTabs(Keys.ARROW_RIGHT, Keys.ARROW_LEFT);
        iterateThroughTabs(Keys.ARROW_DOWN, Keys.ARROW_UP);

    }

    /**
     * Test that will verify that when a tab closes, the active element is moved to either the correct element.
     * 
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public void testFocusOnClose_MovesToAnotherElement() throws MalformedURLException, URISyntaxException {
        open(createURL("basic", "true"));
        NUMBER_OF_TABS = 7;
        // Check focus moves from the first element to the second element after it is closed
        checkFocusMoves(createXPath(1), TITLE_ARRAY[0], TITLE_ARRAY[1]);

        // Check focus moves from middle element to next element
        checkFocusMoves(createXPath(2), TITLE_ARRAY[2], TITLE_ARRAY[3]);

        // Check focus moves from the last element to the second to last element
        checkFocusMoves(createXPath(5), TITLE_ARRAY[6], TITLE_ARRAY[5]);

    }

    /**
     * Test verifying that if an element that is not active is closed, then focus is not lost
     * 
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public void testFocusOnClose_NonCurrentElementDoesntLoseFocus() throws MalformedURLException, URISyntaxException {
        open(createURL("basic", "true"));
        NUMBER_OF_TABS = 7;
        closeTabAndVerify(createXPath(4) + "/a");

        WebElement element = findDomElement(ACTIVE_LI_LOCATOR);
        assertEquals("Correct element was not found", TITLE_ARRAY[4], element.getText());

    }

    /**
     * Dynamically create a component, verify it and make sure that it still acts as a normal component
     * 
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public void testFocusOnClose_DynamicTabGeneration() throws MalformedURLException, URISyntaxException {

        String tabName = "Dynamic";
        String tabBody = "Dynamically generated";
        NUMBER_OF_TABS = 7;

        open(createURL("basic", "false"));
        createNewTab(tabName, tabBody);

        checkFocusMoves(createXPath(8), tabName, TITLE_ARRAY[6]);

    }

    /**
     * Verifying that nestedTabs work the same as normal tabs
     * 
     * @throws MalformedURLException
     * @throws URISyntaxException
     */
    public void testNestedTabsDelete() throws MalformedURLException, URISyntaxException {
        open(createURL("nestedTabs", "false"));
        WebElement el = findDomElement(By.partialLinkText("inner tab 1"));
        el.click();

        el = findDomElement(By.xpath("//li/a/a"));
        el.click();

        // Verify nested tab that was not deleted is active
        verifyElementIsActive(By.xpath("//li[contains(., 'inner tab 2')]"));

        // Verify that the parent tab is still active, and that both elements in the parents tabBar still exist
        verifyElementIsActive(By.xpath("//li[contains(., 'tab1')]"));

        List<WebElement> elements = findDomElements(By.xpath("//div[contains(@class,'nestedTabs')]/div/ul/li"));
        assertEquals("Size of the part tabBar was not as expected. Something must have been deleted", 2,
                elements.size());
    }

    /**
     * Test that will verify that tabbing through tabset should go into the body.
     * 
     * Disabled against mobile since tabbing does not make sense on mobile Tabbing with Safari acts oddly. For some
     * strange reason, I have to grab the element I want and then send the tab key to put it into focus other wise
     * nothing happens
     * 
     * @throws MalformedURLException
     * @throws URISyntaxException
     */

    @ExcludeBrowsers({ BrowserType.SAFARI, BrowserType.ANDROID_PHONE, BrowserType.ANDROID_TABLET, BrowserType.IPHONE,
            BrowserType.IPAD })
    public void testTabbingInTabSet() throws MalformedURLException, URISyntaxException {
        open(createURL("tab", "true"));

        // Focus on tab and move to next focusable element
        WebElement element = findDomElement(By.partialLinkText("Accounts"));
        element.click();
        auraUITestingUtil.pressTab(element);

        // Verify anchor is focused on
        String activeElementText = auraUITestingUtil.getActiveElementText();
        assertTrue("Focus is not on ther correct element", activeElementText.contains("Close"));

        // Move from anchor to next item (inputTextBox)
        element = findDomElement(By.xpath(createXPath(1) + "/a"));
        auraUITestingUtil.pressTab(element);

        // Verify inputTextBox (in tab section) is focused
        verifyElementFocus("inputTabTitle");

        // Tab to the next focusable area
        element = findDomElement(By.cssSelector("input[class*='inputTabTitle']"));
        auraUITestingUtil.pressTab(element);

        // Verify inputTextArea (outside of the tab) is focused
        verifyElementFocus("inputTabContent");
    }
}
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
/*
 * Copyright, 1999-2009, salesforce.com All Rights Reserved Company Confidential
 */
package org.auraframework.integration.test.javascript.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.auraframework.adapter.ComponentLocationAdapter;
import org.auraframework.components.AuraComponentsFiles;
import org.auraframework.def.ActionDef.ActionType;
import org.auraframework.def.ControllerDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.DefDescriptor.DefType;
import org.auraframework.def.RendererDef;
import org.auraframework.def.TestCaseDef;
import org.auraframework.def.TestSuiteDef;
import org.auraframework.impl.AuraImplTestCase;
import org.auraframework.impl.javascript.controller.JavascriptActionDef;
import org.auraframework.impl.javascript.controller.JavascriptControllerDef;
import org.auraframework.impl.javascript.controller.JavascriptPseudoAction;
import org.auraframework.impl.javascript.parser.JavascriptControllerParser;
import org.auraframework.impl.javascript.parser.JavascriptRendererParser;
import org.auraframework.impl.javascript.parser.JavascriptTestSuiteParser;
import org.auraframework.impl.javascript.renderer.JavascriptRendererDef;
import org.auraframework.impl.javascript.testsuite.JavascriptTestCaseDef;
import org.auraframework.impl.javascript.testsuite.JavascriptTestSuiteDef;
import org.auraframework.impl.source.BaseSourceLoader;
import org.auraframework.impl.source.file.FileSourceLoader;
import org.auraframework.impl.source.resource.ResourceSourceLoader;
import org.auraframework.impl.system.DefDescriptorImpl;
import org.auraframework.impl.system.DefinitionImpl;
import org.auraframework.instance.Action.State;
import org.auraframework.system.Source;
import org.auraframework.throwable.AuraRuntimeException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.throwable.quickfix.DefinitionNotFoundException;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.util.ServiceLocator;
import org.auraframework.util.json.JsonEncoder;

/**
 * This class tests the usage of Javascript to specify Controllers, Renderers and Test suites for Aura Components. The
 * Javascript files are parsed using the {@link JavascriptParser} and the corresponding defs are created.
 */
public class JavascriptParserTest extends AuraImplTestCase {
    public JavascriptParserTest(String name) {
        super(name);
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}.
     */
    public void testParse() throws Exception {
        DefDescriptor<TestSuiteDef> descriptor = DefDescriptorImpl.getInstance(
                "js://test.testJSTestSuite", TestSuiteDef.class);
        Source<TestSuiteDef> source = getJavascriptSourceLoader().getSource(descriptor);
        // Step 1: Parse the source which refers to a simple component with a
        // reference to Javascript test suite
        TestSuiteDef testSuite = new JavascriptTestSuiteParser().parse(descriptor, source);
        assertTrue(testSuite instanceof JavascriptTestSuiteDef);
        // Step 2: Gold file the Json output of the test suite object
        serializeAndGoldFile(testSuite, "_JSTestSuite");
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. This test case includes these scenarios:
     * <ul>
     * <li>A null source</li>
     * <li>A null Descriptor</li>
     * <li>Trying to create a Source for a Def type while no corresponding js file is present for the component</li>
     * </ul>
     */
    public void testNullCases() throws Exception {
        DefDescriptor<TestSuiteDef> descriptor = DefDescriptorImpl.getInstance(
                "js://test.testNoJSControllers", TestSuiteDef.class);
        Source<TestSuiteDef> source = getJavascriptSourceLoader().getSource(descriptor);
        boolean failed = false;
        // Test case 1: Try to create a Source for a component which does not
        // have any javascript associated with it
        // getSource() call is looking for testNoJSControllersTest.js in the
        // component folder
        try {
            new JavascriptTestSuiteParser().parse(descriptor, source);
            failed = true;
        } catch (Exception e) {// Expect a file not found Exception
            assertEquals(
                    "Exception must be "
                            + AuraRuntimeException.class.getSimpleName(),
                    AuraRuntimeException.class, e.getClass());
            e.getMessage().contains("testNoJSControllersTest.js");
        }
        assertFalse("Parser should have thrown and exception", failed);
        // Test case 2: Null source
        try {
            new JavascriptTestSuiteParser().parse(descriptor, null);
            fail("should not load null source");
        } catch (Exception e) {
            checkExceptionFull(e, NullPointerException.class, null);
        }
        // Test Case 3: Null component descriptor
        try {
            new JavascriptTestSuiteParser().parse(null, source);
            fail("should not load null component descriptor");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "testNoJSControllers");
        }
    }

    /**
     * Having duplicate controller methods What if there are two actions in the javascript controller with the same
     * name.
     */
    public void testDuplicateJSController() throws Exception {
        DefDescriptor<ControllerDef> descriptor = DefDescriptorImpl
                .getInstance("js://test.testDuplicateJSController",
                        ControllerDef.class);
        Source<ControllerDef> source = getJavascriptSourceLoader().getSource(descriptor);
        ControllerDef controller = new JavascriptControllerParser().parse(descriptor, source);
        assertTrue(controller instanceof JavascriptControllerDef);
        JavascriptControllerDef obj = (JavascriptControllerDef) controller;
        Map<String, JavascriptActionDef> controllerActions = obj
                .getActionDefs();
        assertTrue(controllerActions.containsKey("functionName"));
        // If we have more than one controller function with same name, the
        // later one will replace the previous one
        assertTrue(controllerActions.size() == 1);
        // Verify the only JavascriptAction Def we have
        JavascriptActionDef jsActionDef = null;
        jsActionDef = controllerActions.get("functionName");
        assertEquals(ActionType.CLIENT, jsActionDef.getActionType());
        String[] jsonres = (JsonEncoder.serialize(jsActionDef)).split("\"");
        // Verify the second function did replace the first one
        assertEquals("second function didn't survive",
                "function(component) {var v = 2;}", jsonres[0]);
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. The DefDescriptor in this case is
     * referring to a simple Component with a Javascript Controller {@link DefType#CONTROLLER}.
     */
    public void testJSController() throws Exception {

        DefDescriptor<ControllerDef> descriptor = DefDescriptorImpl
                .getInstance("js://test.testJSController", ControllerDef.class);
        Source<ControllerDef> source = getJavascriptSourceLoader().getSource(descriptor);
        // STEP 1:
        // Parse and create the ControllerDef object for the component
        ControllerDef controller = new JavascriptControllerParser().parse(descriptor, source);

        // STEP 2:
        // 2.1:Verify the CONTROLLERDEF object
        assertTrue(controller instanceof JavascriptControllerDef);
        // Convert from a generic controller to a Javascript type controller
        JavascriptControllerDef obj = (JavascriptControllerDef) controller;
        // Step 4: Verify properties of JavascriptRenderDef Object
        // OBject that is to be verified: Qualified name,
        assertEquals("unexpected qualifiedName of controller",
                "js://test.testJSController", obj.getDescriptor()
                        .getQualifiedName());

        serializeAndGoldFile(controller, "_JSControllerDef");

        // 2.2: Should be able to create an instance of the client action on
        // the server side, but it's a pseudo action
        try {
            obj.createAction("newAction", new HashMap<String, Object>());
            fail("Should not be able to create an instance of the client action on the server side");
        } catch (Exception e) {// Expect a definition not found Exception
            checkExceptionFull(e, DefinitionNotFoundException.class,
                    "No ACTION named js://test.testJSController/ACTION$newAction found");
        }
        JavascriptPseudoAction action = (JavascriptPseudoAction) obj.createAction("functionName1", null);
        assertEquals(State.ERROR, action.getState());

        // 2.3 Extract the action defs and verify each of them in Step 3
        // Get all the actions defined in the Javascript
        Map<String, JavascriptActionDef> controllerActions = obj
                .getActionDefs();

        // STEP 3:
        // 3.1: Verify the number of ACTIONDEF objects is 2
        assertTrue(controllerActions.size() == 2);
        // 3.2: Verify the name of actiodefs
        assertTrue(controllerActions.containsKey("functionName1"));
        assertTrue(controllerActions.containsKey("functionName2"));

        // 3.3: Verify each JavascriptAction Def
        JavascriptActionDef jsActionDef = null;
        // 3.3.1 Action Def 1
        jsActionDef = controllerActions.get("functionName1");
        // Javascript Controllers are to be called on the Client side
        assertEquals(ActionType.CLIENT, jsActionDef.getActionType());
        // Javascript actions have no return type
        assertNull(jsActionDef.getReturnType());
        // Verify the Serialized form of the objects
        serializeAndGoldFile(controllerActions.get("functionName1"),
                "_actionDef_functionName1");

        // OBject that is to be verified, Qualified name
        assertEquals("unexpected qualifiedName for functionName1",
                "js://test.testJSController/ACTION$functionName1", jsActionDef
                        .getDescriptor().getQualifiedName());

        // 3.3.2 Action Def 2
        jsActionDef = controllerActions.get("functionName2");
        // Javascript Controllers are to be called on the Client side
        assertEquals(ActionType.CLIENT, jsActionDef.getActionType());
        // Javascript actions have no return type
        assertNull(jsActionDef.getReturnType());
        // Verify the Serialized form of the objects
        serializeAndGoldFile(controllerActions.get("functionName2"),
                "_actionDef_functionName2");
        // OBject that is to be verified, Qualified name
        assertEquals("unexpected qualifiedName for functionName2",
                "js://test.testJSController/ACTION$functionName2", jsActionDef
                        .getDescriptor().getQualifiedName());

    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. The DefDescriptor in this case is
     * referring to a Nested Component with a Javascript Controller {@link DefType#CONTROLLER}.
     */
    public void testNestedComponent() throws Exception {
        DefDescriptor<ControllerDef> descriptor = DefDescriptorImpl
                .getInstance("js://test.testJSControllerParent",
                        ControllerDef.class);
        Source<ControllerDef> source = getJavascriptSourceLoader().getSource(descriptor);
        // STEP 1:
        // Parse and create the ControllerDef object for the component
        ControllerDef controller = new JavascriptControllerParser().parse(descriptor, source);
        // STEP 2:
        // 2.1:Verify the CONTROLLERDEF object
        assertTrue(controller instanceof JavascriptControllerDef);
        // Convert from a generic controller to a Javascript type controller
        JavascriptControllerDef obj = (JavascriptControllerDef) controller;
        // Get all the actions defined in the Javascript
        Map<String, JavascriptActionDef> controllerActions = obj
                .getActionDefs();

        // STEP 3:
        // 3.1: Verify the number of ACTIONDEF objects
        assertTrue(controllerActions.size() == 1);
        // 3.2: Verify the name of ActionDefs
        assertTrue(controllerActions.containsKey("functionName1"));

        // 3.4: Verify each JavascriptAction Def
        JavascriptActionDef jsActionDef = null;
        // 3.4.1 Action Def 1
        jsActionDef = controllerActions.get("functionName1");
        assertEquals(ActionType.CLIENT, jsActionDef.getActionType());
        assertNull(jsActionDef.getReturnType());
        // 3.4.2: Verify the Serialized form of the objects
        serializeAndGoldFile(jsActionDef, "_actionDef_functionName1");

        // OBject that is to be verified, Qualified name
        assertEquals("unexpected qualifiedName of functionName1",
                "js://test.testJSControllerParent/ACTION$functionName1",
                jsActionDef.getDescriptor().getQualifiedName());
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. The DefDescriptor is referring to a
     * Controller but the Javascript should have had only functions. In this scenario there is a variable declaration
     * which is not expected in a controller file. The JSparser will flag an exception for this.
     * 
     * @throws Exception
     */
    public void testInvalidJSController() throws Exception {
        DefDescriptor<ControllerDef> descriptor = DefDescriptorImpl.getInstance("js://test.testInvalidJSController",
                ControllerDef.class);
        Source<ControllerDef> source = getJavascriptSourceLoader().getSource(descriptor);
        ControllerDef cd = new JavascriptControllerParser().parse(descriptor, source);
        try {
            cd.validateDefinition();
            fail("Javascript controller must only contain functions");
        } catch (Exception e) {
            this.checkExceptionContains(e, InvalidDefinitionException.class, "Expected ':'");
        }
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. The DefDescriptor is referring to a
     * Controller but the Javascript should have had only functions. In this scenario, the contents of the js file is a
     * well formatted Json but it contains a string assignment to a map key.
     * 
     * @throws Exception
     */
    public void testNonFunctionElementsInJSController() throws Exception {
        DefDescriptor<ControllerDef> descriptor = DefDescriptorImpl
                .getInstance("js://test.testNonFunctionElementsInJSController",
                        ControllerDef.class);
        Source<ControllerDef> source = getJavascriptSourceLoader().getSource(descriptor);
        ControllerDef cd = new JavascriptControllerParser().parse(descriptor, source);
        try {
            cd.validateDefinition();
            fail("Javascript controller must only contain functions");
        } catch (Exception e) {
        	this.checkExceptionContains(e, InvalidDefinitionException.class,
                            "JsonStreamParseException");
        }
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. The DefDescriptor in this case is
     * referring to a simple Component with a Javascript Rederer {@link DefType#RENDERER}.
     * 
     * @newTestCase Verify the serialized format of Javascript RendererDef.
     * @hierarchy Aura.Unit Tests.Components.Renderer
     * @priority medium
     * @userStorySyncIdOrName a07B0000000Ekdr
     */
    public void testJSRenderer() throws Exception {
        DefDescriptor<RendererDef> descriptor = DefDescriptorImpl.getInstance(
                "js://test.testJSRenderer", RendererDef.class);
        Source<RendererDef> source = getJavascriptSourceLoader().getSource(descriptor);
        // STEP 1:
        // Parse and create the RedererDef object for the component
        RendererDef renderer = new JavascriptRendererParser().parse(descriptor, source);

        // STEP 2:Verify the RENDERERDEF object
        assertTrue(renderer instanceof JavascriptRendererDef);
        // Convert from a generic DEFINITION to a Javascript Renderer Definition
        JavascriptRendererDef obj = (JavascriptRendererDef) renderer;
        // Step 3: Gold file the JAvascriptRenderDef
        serializeAndGoldFile(renderer, "_JSRendererDef");
        // Step 4: Verify properties of JavascriptRenderDef Object
        // OBject that is to be verified, Qualified name,
        assertEquals("unexpected qualifiedName of renderer",
                "js://test.testJSRenderer", obj.getDescriptor()
                        .getQualifiedName());
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. The DefDescriptor in this case is
     * referring to a simple Component with an invalid Javascript Rederer {@link DefType#RENDERER}. Javascript rederer
     * should have only two actions : render & rerender
     */
    // TODO: W-689596 is a broad bug that would cover this case.
    // Uncommnet the test once validation is added in
    // JavascriptRendererDefHandler.
    public void _testInvalidJSRenderer() throws Exception {
        DefDescriptor<RendererDef> descriptor = DefDescriptorImpl.getInstance(
                "js://test.testInvalidJSRenderer", RendererDef.class);
        Source<RendererDef> source = getJavascriptSourceLoader().getSource(descriptor);
        try {
            // Parse and create the RedererDef object for the component
            new JavascriptRendererParser().parse(descriptor, source);
            fail("Javascript renderer should have only two actions : render & rerender");
        } catch (AuraRuntimeException expected) {
            // this test is disabled, update error msg when we enable the test
            checkExceptionStart(expected, AuraRuntimeException.class, null);
        }
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. The DefDescriptor in this case is
     * referring to a simple Component with a Javascript test {@link DefType#TESTSUITE}.
     */
    public void testJSTestSuite() throws Exception {

        DefDescriptor<TestSuiteDef> descriptor = DefDescriptorImpl.getInstance(
                "js://test.testJSTestSuite", TestSuiteDef.class);
        Source<TestSuiteDef> source = getJavascriptSourceLoader().getSource(descriptor);

        // Step 1: Parse the source which refers to a simple component with a
        // reference to Javascript test suite
        TestSuiteDef testSuite = new JavascriptTestSuiteParser().parse(descriptor, source);
        assertTrue(testSuite instanceof JavascriptTestSuiteDef);

        // Step 2: Gold file the Json output of the test suite object
        serializeAndGoldFile(testSuite, "_JSTestSuite");

        // Step 3: Verify the properties of the JavascriptTestSuiteDef object
        // OBject that is to be verified, Qualified name,
        assertEquals("unexpected qualifiedName of testSuite",
                "js://test.testJSTestSuite",
                ((JavascriptTestSuiteDef) testSuite).getDescriptor()
                        .getQualifiedName());
        // Step 4: Verify each testCaseDef objects in the test suite object
        List<TestCaseDef> testCases = ((JavascriptTestSuiteDef) testSuite)
                .getTestCaseDefs();
        assertEquals(3, testCases.size());
        for (Object o : testCases.toArray()) {
            assertTrue(o instanceof JavascriptTestCaseDef);
            JavascriptTestCaseDef testCaseDef = (JavascriptTestCaseDef) o;
            Map<String, Object> attributes = testCaseDef.getAttributeValues();
            if (testCaseDef.getName().equals("testHelloWorld")) {
                assertTrue(attributes.size() == 1);
                assertTrue(attributes.containsKey("num"));
                assertEquals("2", attributes.get("num"));
                // OBject that is to be verified, Qualified name
                assertEquals("unexpected qualifiedName of testHelloWorld",
                        "js://test.testJSTestSuite/TESTCASE$testHelloWorld",
                        ((DefinitionImpl<?>) o).getDescriptor()
                                .getQualifiedName());
            } else if (testCaseDef.getName().equals("testHelloWorld2")) {
                assertTrue(attributes.size() == 1);
                assertTrue(attributes.containsKey("num"));
                // Should get the default Attribute value
                assertEquals("5", attributes.get("num"));
                // OBject that is to be verified, Qualified name,
                assertEquals("unexpected qualifiedName of testHelloWorld2",
                        "js://test.testJSTestSuite/TESTCASE$testHelloWorld2",
                        ((DefinitionImpl<?>) o).getDescriptor()
                                .getQualifiedName());
            } else if (testCaseDef.getName().equals("testHelloWorld3")) {
                assertTrue(attributes.size() == 2);
                assertTrue(attributes.containsKey("num"));
                assertEquals("4", attributes.get("num"));
                assertTrue(attributes.containsKey("alpha"));
                assertEquals("A", attributes.get("alpha"));
                // OBject that is to be verified, Qualified name
                assertEquals("unexpected qualifiedName of testHelloWorld3",
                        "js://test.testJSTestSuite/TESTCASE$testHelloWorld3",
                        ((DefinitionImpl<?>) o).getDescriptor()
                                .getQualifiedName());
            } else {
                fail("There should be no other test cases created");
            }

        }
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. The DefDescriptor in this case is
     * referring to a simple Component with a Javascript test suite {@link DefType#TESTSUITE}. One of the test cases has
     * no function assigned to them, this should cause an Exception
     */

    public void testJSTestSuiteWithoutAttributes() throws Exception {
        DefDescriptor<TestSuiteDef> descriptor = DefDescriptorImpl.getInstance(
                "js://test.testJSTestSuiteWithoutAttributes",
                TestSuiteDef.class);
        Source<TestSuiteDef> source = getJavascriptSourceLoader().getSource(descriptor);

        // Step 1: Parse the source which refers to a simple component with a
        // reference to Javascript test suite
        TestSuiteDef testSuite = new JavascriptTestSuiteParser().parse(descriptor, source);
        assertTrue(testSuite instanceof JavascriptTestSuiteDef);

        // Step 2: Verify the properties of the JavascriptTestSuiteDef object
        // OBject that is to be verified, Qualified name,
        assertEquals("unexpected qualifiedName of testSuite",
                "js://test.testJSTestSuiteWithoutAttributes",
                ((JavascriptTestSuiteDef) testSuite).getDescriptor()
                        .getQualifiedName());
        // Step 3: Verify each testCaseDef objects in the test suite object
        List<TestCaseDef> testCases = ((JavascriptTestSuiteDef) testSuite)
                .getTestCaseDefs();
        assertEquals(2, testCases.size());
        for (Object o : testCases.toArray()) {
            assertTrue(o instanceof JavascriptTestCaseDef);
            JavascriptTestCaseDef testCaseDef = (JavascriptTestCaseDef) o;
            Map<String, Object> attributes = testCaseDef.getAttributeValues();
            if (testCaseDef.getName().equals("testHelloWorld")) {
                assertTrue(attributes.size() == 1);
                assertTrue(attributes.containsKey("num"));
                assertEquals("2", attributes.get("num"));
                // OBject that is to be verified, Qualified name
                assertEquals(
                        "unexpected qualifiedName of testHelloWorld",
                        "js://test.testJSTestSuiteWithoutAttributes/TESTCASE$testHelloWorld",
                        ((DefinitionImpl<?>) o).getDescriptor()
                                .getQualifiedName());
            } else if (testCaseDef.getName().equals("testHelloWorld3")) {
                assertNull(attributes);
                // OBject that is to be verified, Qualified name
                assertEquals(
                        "unexpected qualifiedName of testHelloWorld3",
                        "js://test.testJSTestSuiteWithoutAttributes/TESTCASE$testHelloWorld3",
                        ((DefinitionImpl<?>) o).getDescriptor()
                                .getQualifiedName());
            } else {
                fail("There should be no other test cases created");
            }
        }
    }

    /**
     * Test method for {@link JavascriptParser#parse(DefDescriptor, Source)}. The DefDescriptor in this case is
     * referring to a simple Component with a Javascript test suite {@link DefType#TESTSUITE}. One of the test cases has
     * no function assigned to them, this should cause an Exception
     */
    public void testJSTestSuiteWithoutTestFunc() throws Exception {
        assertInvalidTestCase("{testWithString:{test:'empty'}}",
                "testWithString 'test' must be a function or an array of functions");
        assertInvalidTestCase("{testWithObject:{test:{}}}",
                "testWithObject 'test' must be a function or an array of functions");
        assertInvalidTestCase(
                "{testWithMixedArray:{test:[function(){},'oops']}}",
                "testWithMixedArray 'test' must be a function or an array of functions");
        assertInvalidTestCase(
                "{testWithObjectFunction:{test:{inner:function(){}}}}",
                "testWithObjectFunction 'test' must be a function or an array of functions");
    }

    private void assertInvalidTestCase(String suiteContent, String expectedMessageStartsWith) throws Exception {
        DefDescriptor<TestSuiteDef> desc = addSourceAutoCleanup(TestSuiteDef.class, suiteContent);
        Source<TestSuiteDef> source = getSource(desc);
        TestSuiteDef d = new JavascriptTestSuiteParser().parse(desc, source);
        try {
            d.validateDefinition();
            fail("Invalid testsuite: Every test case should have a function assigned to it");
        } catch (QuickFixException expected) {
            assertTrue(expected.getMessage().startsWith(expectedMessageStartsWith));
        }
    }

    private BaseSourceLoader getJavascriptSourceLoader() {
        if (AuraComponentsFiles.TestComponents.asFile().exists()) {
            return new FileSourceLoader(AuraComponentsFiles.TestComponents.asFile());
        } else {
            String pkg = ServiceLocator.get()
                    .get(ComponentLocationAdapter.class, "auraTestComponentLocationAdapterImpl")
                    .getComponentSourcePackage();
            return new ResourceSourceLoader(pkg);
        }
    }
}

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
package org.auraframework.test.testsetrunner;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.Map;
import java.util.SortedMap;

import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.auraframework.system.AuraContext;
import org.auraframework.test.ComponentJSTest.ComponentTestCase;
import org.auraframework.test.perf.util.PerfExecutorTest;
import org.auraframework.util.ServiceLocator;
import org.auraframework.util.test.util.TestInventory;
import org.auraframework.util.test.util.TestInventory.Type;

import com.google.common.collect.Maps;

/**
 * An encapsulation of all of the state held by the {@link TestSetRunnerModel}.
 * This state is not kept in the model itself because it is currently impossible
 * to create lazy singleton objects that adhere to the contract of {@link Model}
 * .
 *
 * FIXME: This setup is not scoped to a user or page state. Two users can stomp
 * on each other's test results.
 *
 * FIXME: There is no stickiness to ensure that client side polls are reaching
 * the server that is running tests on its behalf if the deploy has multiple
 * appServers.
 *
 * FIXME: Individual tests are tracked with just a bag of properties rather than
 * as a strongly typed client-visible model.
 */
@ThreadSafe
public class TestSetRunnerState {
    /**
     * A helper to allow for lazy initialization of the the
     * {@link TestSetRunnerState}.
     */
    private static class FuncSingletonHolder {
        private static TestSetRunnerState FUNC_INSTANCE = new TestSetRunnerState(TestInventory.FUNC_TESTS);
    }

    private static class PerfSingletonHolder {
        private static TestSetRunnerState PERF_INSTANCE = new TestSetRunnerState(TestInventory.PERF_TESTS);
    }
    /**
     * The inventory tracks all test cases available for execution.
     */
    @GuardedBy("this")
    private Map<String, Test> inventory = Maps.newHashMap();

    /**
     * Tracks all test cases type available for execution.
     */
    @GuardedBy("this")
    private Map<String, Type> testCasesType = Maps.newHashMap();

    /**
     * Parallel to the inventory, this map is used as a data bag to store
     * various properties about tests (e.g. status, exceptions, etc...)
     */
    @GuardedBy("this")
    private SortedMap<String, Map<String, Object>> testsWithPropsMap = Maps.newTreeMap();

    /**
     * Return the type of Instance based on scope
     * @return the singleton instance.
     */
    public static TestSetRunnerState getInstanceByScope (String scope) {
    	if (scope != null && scope.equalsIgnoreCase("perf")) {
			return TestSetRunnerState.getPerfInstance();
		} else {
			return TestSetRunnerState.getFuncInstance();
		}
    }
    
    public static TestSetRunnerState getFuncInstance() {
        return FuncSingletonHolder.FUNC_INSTANCE;
    }

    public static TestSetRunnerState getPerfInstance() {
        return PerfSingletonHolder.PERF_INSTANCE;
    }

    private TestSetRunnerState(EnumSet<TestInventory.Type> scope) {
        populateInventory(scope);
    }

    private TestSetRunnerState() {
        this(TestInventory.ALL_TESTS);
    }

    /**
     * @return an unmodifiable view of the test inventory.
     */
    public synchronized Map<String, Test> getInventory() {
        return Collections.unmodifiableMap(inventory);
    }

    /**
     * @return an unmodifiable view of the test properties map.
     */
    public synchronized Map<String, Map<String, Object>> getTestsWithPropertiesMap() {
        return Collections.unmodifiableMap(testsWithPropsMap);
    }

    /**
     * Populates the model by querying for all implementations of
     * {@link TestInventory}.
     * @param scope
     */
    private synchronized void populateInventory(EnumSet<Type> scope) {
        // Load the inventory in a separate thread.
        InventoryPopulator populator = new InventoryPopulator(scope);
        Thread t = new Thread(populator, "TestSetRunnerState Inventory Populator");
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * We load the test inventory in a separate thread because some test
     * constructors start/stop the {@link AuraContext}. If we load them in the
     * requesting thread, they end up corrupting the context for the
     * {@link TestSetRunnerController}.
     */
    private class InventoryPopulator implements Runnable {
        private EnumSet<Type> scope;

        public InventoryPopulator(EnumSet<Type> scope) {
            this.scope = scope;
        }

        @Override
        public void run() {
            Collection<TestInventory> inventories = ServiceLocator.get().getAll(TestInventory.class);
            for (TestInventory i : inventories) {
                for (Type type : scope) {
                    TestSuite suite = i.getTestSuite(type);
                    if (suite.testCount() > 0) {
                        addSuite(type, suite);
                    }
                }
            }

            for (Test t : inventory.values()) {
                Map<String, Object> testWithProps = Maps.newHashMap();
                testWithProps.put("name", t.toString());
                testWithProps.put("selected", false);
                testWithProps.put("status", "Not Run Yet");
                testWithProps.put("exception", "");
                testWithProps.put("isHidden", "");
                testWithProps.put("type", testCasesType.get(t.toString()).toString().toLowerCase());
                testWithProps.put("isPerf", t instanceof PerfExecutorTest);
                testWithProps.put("perfInfo", "");

                String url = "";
                if (t instanceof ComponentTestCase) {
                    url = ((ComponentTestCase) t).getAppUrl();
                }

                if (t instanceof PerfExecutorTest) {
                    url = ((PerfExecutorTest) t).generateUrl();
                }

                testWithProps.put("jsConsole", url);
                testsWithPropsMap.put(t.toString(), testWithProps);
            }
        }

        /**
         * @param suite the suite to add to the model.
         */
        private void addSuite(Type type, TestSuite suite) {
            for (Enumeration<Test> e = suite.tests(); e.hasMoreElements();) {
                Test test = e.nextElement();
                if (test instanceof TestSuite) {
                    addSuite(type, (TestSuite) test);
                } else {
                    inventory.put(test.toString(), test);
                    testCasesType.put(test.toString(), type);
                }
            }
        }
    }

    /**
     * Modify a property of a test
     *
     * @param test identifies the test
     * @param key the key of the property
     * @param value the new value
     */
    public synchronized void setTestProp(String test, String key, Object value) {
        Map<String, Object> testProps = testsWithPropsMap.get(test);
        testProps.put(key, value);
    }
}

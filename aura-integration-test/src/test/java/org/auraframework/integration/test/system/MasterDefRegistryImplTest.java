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
package org.auraframework.integration.test.system;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import javax.annotation.Nonnull;

import org.auraframework.Aura;
import org.auraframework.adapter.ConfigAdapter;
import org.auraframework.adapter.RegistryAdapter;
import org.auraframework.cache.Cache;
import org.auraframework.def.ApplicationDef;
import org.auraframework.def.ClientLibraryDef;
import org.auraframework.def.ComponentDef;
import org.auraframework.def.ControllerDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.DefDescriptor.DefType;
import org.auraframework.def.Definition;
import org.auraframework.def.DefinitionAccess;
import org.auraframework.def.DescriptorFilter;
import org.auraframework.def.HelperDef;
import org.auraframework.def.RendererDef;
import org.auraframework.def.StyleDef;
import org.auraframework.def.TypeDef;
import org.auraframework.impl.AuraImpl;
import org.auraframework.impl.AuraImplTestCase;
import org.auraframework.impl.system.DefDescriptorImpl;
import org.auraframework.impl.system.MasterDefRegistryImpl;
import org.auraframework.instance.BaseComponent;
import org.auraframework.service.CachingService;
import org.auraframework.service.ContextService;
import org.auraframework.system.AuraContext;
import org.auraframework.system.AuraContext.Authentication;
import org.auraframework.system.AuraContext.Format;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.system.DefRegistry;
import org.auraframework.system.DependencyEntry;
import org.auraframework.system.Location;
import org.auraframework.system.MasterDefRegistry;
import org.auraframework.system.Source;
import org.auraframework.system.SourceListener;
import org.auraframework.system.SubDefDescriptor;
import org.auraframework.test.source.StringSourceLoader;
import org.auraframework.test.util.AuraTestingUtil;
import org.auraframework.throwable.NoAccessException;
import org.auraframework.throwable.quickfix.DefinitionNotFoundException;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.json.Json;
import org.auraframework.util.test.annotation.ThreadHostileTest;
import org.auraframework.util.test.util.AuraPrivateAccessor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * @see org.auraframework.impl.registry.RootDefFactoryTest
 */
@ThreadHostileTest("Don't you go clearing my caches.")
public class MasterDefRegistryImplTest extends AuraImplTestCase {
    @Mock
    Definition globalDef;
    @Mock
    DefinitionAccess defAccess;
    @Mock
    DefDescriptor<ComponentDef> referencingDesc;
    @Mock
    Cache<String, String> mockAccessCheckCache;

    public MasterDefRegistryImplTest(String name) {
        super(name);
    }

    private MasterDefRegistryImplOverride getDefRegistry(boolean asMocks) {
        Collection<RegistryAdapter> providers = AuraImpl.getRegistryAdapters();
        List<DefRegistry<?>> mdrregs = Lists.newArrayList();

        AuraContext context = Aura.getContextService().getCurrentContext();
        for (RegistryAdapter provider : providers) {
            DefRegistry<?>[] registries = provider.getRegistries(context.getMode(), context.getAccess(), null);
            if (registries != null) {
                for (DefRegistry<?> reg : registries) {
                    Set<String> ns = reg.getNamespaces();

                    if (ns != null) {
                        mdrregs.add(asMocks ? Mockito.spy(reg) : reg);
                    }
                }
            }
        }
        MasterDefRegistryImplOverride registry = new MasterDefRegistryImplOverride(
                mdrregs.toArray(new DefRegistry<?>[mdrregs.size()]));
        return asMocks ? Mockito.spy(registry) : registry;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void spyOnDefs(final MasterDefRegistryImplOverride registry) throws QuickFixException {
        final MockUtil mockUtil = new MockUtil();
        for (DefRegistry<?> subReg : registry.getAllRegistries()) {
            Mockito.doAnswer(new Answer<Definition>() {
                @Override
                public Definition answer(InvocationOnMock invocation) throws Throwable {
                    Definition ret = (Definition) invocation.callRealMethod();
                    if (ret == null) {
                        return ret;
                    }
                    if (mockUtil.isMock(ret)) {
                        return ret;
                    } else {
                        ret = Mockito.spy(ret);
                        registry.addLocalDef(ret);
                        return ret;
                    }
                }
            }).when(subReg).getDef(Mockito.<DefDescriptor> any());
        }
    }

    /**
     * Verify some of the assertions (copied here) made by compileDef (excluding #2 & #5).
     * <ol>
     * <li>Each definition has 'validateDefinition()' called on it exactly once.</li>
     * <li>No definition is marked as valid until all definitions in the dependency set have been validated</li>
     * <li>Each definition has 'validateReferences()' called on it exactly once, after the definitions have been put in
     * local cache</li>
     * <li>All definitions are marked valid by the DefRegistry after the validation is complete</li>
     * <li>No definition should be available to other threads until it is marked valid</li>
     * <ol>
     */
    private void assertCompiledDef(Definition def) throws QuickFixException {
        Mockito.verify(def, Mockito.times(1)).validateDefinition();
        Mockito.verify(def, Mockito.times(1)).validateReferences();
        Mockito.verify(def, Mockito.times(1)).markValid();
        assertEquals("definition not valid: " + def, true, def.isValid());
    }

    private void assertIdenticalDependencies(DefDescriptor<?> desc1, DefDescriptor<?> desc2) throws Exception {
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        Set<DefDescriptor<?>> deps1 = registry.getDependencies(registry.getUid(null, desc1));
        Set<DefDescriptor<?>> deps2 = registry.getDependencies(registry.getUid(null, desc2));
        assertNotNull(deps1);
        assertNotNull(deps2);
        assertEquals("Descriptors should have the same number of dependencies", deps1.size(), deps2.size());

        // Loop through and check individual dependencies. Order doesn't matter.
        for (DefDescriptor<?> dep : deps1) {
            assertTrue("Descriptors do not have identical dependencies",
                    checkDependenciesContains(deps2, dep.getQualifiedName()));
        }
    }

    private boolean checkDependenciesContains(Set<DefDescriptor<?>> deps, String depSearch) {
        for (DefDescriptor<?> dep : deps) {
            if (dep.getQualifiedName().equals(depSearch)) {
                return true;
            }
        }
        return false;
    }

    public void testFindRegex() throws Exception {
        String namespace = "testFindRegex" + getAuraTestingUtil().getNonce();
        DefDescriptor<ApplicationDef> houseboat = addSourceAutoCleanup(ApplicationDef.class,
                String.format(baseApplicationTag, "", ""), String.format("%s:houseboat", namespace));
        addSourceAutoCleanup(ApplicationDef.class, String.format(baseApplicationTag, "", ""),
                String.format("%s:houseparty", namespace));
        addSourceAutoCleanup(ApplicationDef.class, String.format(baseApplicationTag, "", ""),
                String.format("%s:pantsparty", namespace));

        MasterDefRegistryImplOverride masterDefReg = getDefRegistry(false);

        assertTrue("find() not finding all sources",
                masterDefReg.find(new DescriptorFilter(String.format("markup://%s:*", namespace))).size() == 3);
        assertEquals("find() fails with wildcard as prefix", 1,
                masterDefReg.find(new DescriptorFilter("*://" + houseboat.getDescriptorName())).size());
        assertEquals("find() fails with wildcard as namespace", 1,
                masterDefReg.find(new DescriptorFilter("markup://*:" + houseboat.getName())).size());
        assertEquals("find() fails with wildcard as name", 1,
                masterDefReg.find(new DescriptorFilter(houseboat.getQualifiedName())).size());
        assertEquals("find() fails with wildcard at end of name", 2,
                masterDefReg.find(new DescriptorFilter(String.format("markup://%s:house*", namespace))).size());
        assertEquals("find() fails with wildcard at beginning of name", 2,
                masterDefReg.find(new DescriptorFilter(String.format("markup://%s:*party*", namespace))).size());

        assertEquals("find() should not find nonexistent name", 0,
                masterDefReg.find(new DescriptorFilter(String.format("markup://%s:househunters", namespace))).size());
        assertEquals("find() should not find nonexistent name ending with wildcard", 0,
                masterDefReg.find(new DescriptorFilter(String.format("markup://%s:househunters*", namespace))).size());
        assertEquals("find() should not find nonexistent name with preceeding wildcard", 0,
                masterDefReg.find(new DescriptorFilter(String.format("markup://%s:*notherecaptain", namespace))).size());
    }

    /**
     * TODO: remove try block when find can handle DefType without a default prefix
     */
    public void testFindDefTypeWithoutDefaultPrefix() throws Exception {
        MasterDefRegistryImplOverride masterDefReg = getDefRegistry(false);
        try {
            assertEquals("find() not expecting any matches", 0,
                    masterDefReg.find(new DescriptorFilter("*://doesnt:exist", DefType.TESTCASE)).size());
            fail("If you have fixed this bug, please update the expected results (remove the try block)");
        } catch (NullPointerException npe) {
            // this shouldn't happen and should be fixed
        }
    }

    public void testFindDefTypeWithDefaultPrefix() throws Exception {
        MasterDefRegistryImplOverride masterDefReg = getDefRegistry(false);
        DefDescriptor<ApplicationDef> target = addSourceAutoCleanup(ApplicationDef.class,
                String.format(baseApplicationTag, "", ""));
        assertEquals("unexpected matches found", 1,
                masterDefReg.find(new DescriptorFilter("*://" + target.getDescriptorName(), DefType.APPLICATION))
                        .size());
    }

    private static class AddableDef<T extends Definition> {
        private final Class<T> defClass;
        private final String format;
        private final String content;

        public AddableDef(Class<T> defClass, String format, String content) {
            this.defClass = defClass;
            this.format = format;
            this.content = content;
        }

        public Class<T> getDefClass() {
            return this.defClass;
        }

        public String getFQN(String namespace, String name) {
            return String.format(this.format, namespace, name);
        }

        public String getContent() {
            return content;
        }
    }

    private static AddableDef<?> addable[] = new AddableDef[] {
            // Ignoring top level bundle defs.
            // APPLICATION(ApplicationDef.class, Format.XML, DefDescriptor.MARKUP_PREFIX, ":"),
            // COMPONENT(ComponentDef.class, Format.XML, DefDescriptor.MARKUP_PREFIX, ":"),
            // EVENT(EventDef.class, Format.XML, DefDescriptor.MARKUP_PREFIX, ":"),
            // INTERFACE(InterfaceDef.class, Format.XML, DefDescriptor.MARKUP_PREFIX, ":"),
            // LAYOUTS(LayoutsDef.class, Format.XML, DefDescriptor.MARKUP_PREFIX, ":"),
            new AddableDef<>(ControllerDef.class, "js://%s.%s",
                    "({method: function(cmp) {}})"),
            new AddableDef<>(HelperDef.class, "js://%s.%s",
                    "({method: function(cmp) {}})"),
            // new AddableDef<ProviderDef>(ProviderDef.class, "js://%s.%s",
            // "({provide: function(cmp) {}})"),
            new AddableDef<>(RendererDef.class, "js://%s.%s",
                    "({render: function(cmp) {}})"),
            new AddableDef<>(StyleDef.class, "css://%s.%s",
                    ".THIS {display:block;}"),
            // Ignoring TESTSUITE(TestSuiteDef.class, Format.JS, DefDescriptor.JAVASCRIPT_PREFIX, "."),
            // Ignoring TOKENS(TokensDef.class, Format.XML, DefDescriptor.MARKUP_PREFIX, ":");
    };

    private MasterDefRegistry resetDefRegistry() {
        ContextService contextService = Aura.getContextService();
        if (contextService.isEstablished()) {
            contextService.endContext();
        }
        contextService.startContext(Mode.UTEST, Format.JSON, Authentication.AUTHENTICATED);
        return contextService.getCurrentContext().getDefRegistry();
    }

    private <T extends Definition> void checkAddRemove(DefDescriptor<?> tld, String suid,
            AddableDef<T> toAdd) throws QuickFixException {
        DefDescriptor<T> dd;
        String uid, ouid;
        Set<DefDescriptor<?>> deps;
        AuraTestingUtil util = getAuraTestingUtil();
        MasterDefRegistry mdr;

        dd = DefDescriptorImpl.getInstance(toAdd.getFQN(tld.getNamespace(), tld.getName()),
                toAdd.getDefClass());
        util.addSourceAutoCleanup(dd, toAdd.getContent());
        mdr = resetDefRegistry();
        uid = mdr.getUid(null, tld);
        assertFalse("UID should change on add for " + dd.getDefType() + "@" + dd, suid.equals(uid));
        deps = mdr.getDependencies(uid);
        assertTrue("dependencies should contain the newly created " + dd.getDefType() + "@" + dd,
                deps.contains(dd));
        ouid = uid;
        util.removeSource(dd);
        mdr = resetDefRegistry();
        uid = mdr.getUid(null, tld);
        assertNotSame("UID should change on removal for " + dd.getDefType() + "@" + dd, ouid, uid);
        deps = mdr.getDependencies(uid);
        assertFalse("dependencies should not contain the deleted " + dd, deps.contains(dd));
    }

    private <T extends Definition> void checkOneTLD(String fqn, Class<T> clazz, String content)
            throws QuickFixException {
        AuraTestingUtil util = getAuraTestingUtil();
        String uid;

        DefDescriptor<T> tld = DefDescriptorImpl.getInstance(fqn, clazz);
        util.addSourceAutoCleanup(tld, content);
        MasterDefRegistry mdr = resetDefRegistry();
        // prime the cache.
        uid = mdr.getUid(null, tld);
        assertNotNull(tld + " did not give us a UID", uid);
        for (AddableDef<?> adding : addable) {
            checkAddRemove(tld, uid, adding);
        }
        util.removeSource(tld);
    }

    public void testComponentChChChChanges() throws Exception {
        checkOneTLD("markup://chchch:changes" + getAuraTestingUtil().getNonce(),
                ComponentDef.class, "<aura:component></aura:component>");
    }

    public void testApplicationChChChChanges() throws Exception {
        checkOneTLD("markup://chchch:changes" + getAuraTestingUtil().getNonce(),
                ApplicationDef.class, "<aura:application></aura:application>");
    }

    public void testStringCache() throws Exception {
        String namespace = "testStringCache" + getAuraTestingUtil().getNonce();
        DefDescriptor<ApplicationDef> houseboat = addSourceAutoCleanup(ApplicationDef.class,
                String.format(baseApplicationTag, "", ""), String.format("%s:houseboat", namespace));
        MasterDefRegistryImplOverride masterDefReg = getDefRegistry(false);
        String uid = masterDefReg.getUid(null, houseboat);
        assertNull("Found string in new MDR", masterDefReg.getCachedString(uid, houseboat, "test1"));
        masterDefReg.putCachedString(uid, houseboat, "test1", "value");
        assertEquals("value", masterDefReg.getCachedString(uid, houseboat, "test1"));
    }

    public void testNonPrivilegedStringCache() throws Exception {
        String namespace = "testNonPrivilegedStringCache" + getAuraTestingUtil().getNonce();

        ConfigAdapter configAdapter = Aura.getConfigAdapter();
        assertFalse(namespace + "  should not have been isPriveleged", configAdapter.isPrivilegedNamespace(namespace));
        DefDescriptor<ApplicationDef> houseboat = getAuraTestingUtil().addSourceAutoCleanup(ApplicationDef.class,
                String.format(baseApplicationTag, "", ""), String.format("%s:houseboat", namespace), false);
        MasterDefRegistryImplOverride masterDefReg = getDefRegistry(false);
        String uid = masterDefReg.getUid(null, houseboat);
        assertNull("Found string in new MDR", masterDefReg.getCachedString(uid, houseboat, "test1"));
        masterDefReg.putCachedString(uid, houseboat, "test1", "value");
        assertNull("Found string in new MDR", masterDefReg.getCachedString(uid, houseboat, "test1"));
    }

    public void testGetUidClientOutOfSync() throws Exception {
        String namespace = "testStringCache" + getAuraTestingUtil().getNonce();
        String namePrefix = String.format("%s:houseboat", namespace);
        DefDescriptor<ApplicationDef> houseboat = addSourceAutoCleanup(ApplicationDef.class,
                String.format(baseApplicationTag, "", ""), namePrefix);
        MasterDefRegistryImplOverride masterDefReg = getDefRegistry(false);
        String uid = masterDefReg.getUid(null, houseboat);
        assertNotNull(uid);
        // Check unchanged app gets same UID value
        assertEquals(uid, masterDefReg.getUid(uid, houseboat));

        //
        // When given an incorrect UID, masterDefReg simply returns the correct one.
        String newUid = masterDefReg.getUid(uid + " or not", houseboat);
        assertEquals(uid, newUid);
    }

    /**
     * Verify getting the UID of a dependency doesn't affect the original UID.
     */
    public void testUidDependencies() throws Exception {
        DefDescriptor<ComponentDef> child = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component></aura:component>", "testUidDependenciesChild");
        DefDescriptor<ApplicationDef> parent = addSourceAutoCleanup(ApplicationDef.class,
                "<aura:application><" + child.getDescriptorName() + "/></aura:application>",
                "testUidDependenciesParent");

        MasterDefRegistryImplOverride masterDefReg1 = getDefRegistry(false);
        String parentUid1 = masterDefReg1.getUid(null, parent);

        MasterDefRegistryImplOverride masterDefReg2 = getDefRegistry(false);
        masterDefReg2.getUid(null, child);
        String parentUid2 = masterDefReg2.getUid(null, parent);

        assertTrue("UIDs do not match after getting a dependencies UID", parentUid1.equals(parentUid2));
    }

    /**
     * Verify UID values and dependencies against a gold file.
     *
     * This does a recursive set of dependencies checks to build a gold file with the resulting descriptors and UIDs to
     * ensure that we get both a valid set and can tell what changed (and thus verify that it should have changed).
     *
     * The format of the file is:
     * <ul>
     * <li>Top level descriptor ':' global UID.
     * <li>
     * <li>dependency ':' own hash
     * <li>
     * <li>...</li>
     * </ul>
     */
    public void testUidValue() throws Exception {
        StringBuilder buffer = new StringBuilder();
        String cmpName = "ui:outputNumber";
        DefDescriptor<ComponentDef> desc = Aura.getDefinitionService()
                .getDefDescriptor(cmpName, ComponentDef.class);
        MasterDefRegistryImplOverride masterDefReg = getDefRegistry(false);
        String uid = masterDefReg.getUid(null, desc);
        assertNotNull("Could not retrieve UID for component " + cmpName, uid);
        Set<DefDescriptor<?>> dependencies = masterDefReg.getDependencies(uid);
        assertNotNull("Could not retrieve dependencies for component " + cmpName, dependencies);

        buffer.append(desc.toString());
        buffer.append(" : ");
        buffer.append(uid);
        buffer.append("\n");

        for (DefDescriptor<?> dep : dependencies) {
            buffer.append(dep);
            buffer.append(" : ");
            buffer.append(masterDefReg.getDef(dep).getOwnHash());
            buffer.append("\n");
        }
        goldFileText(buffer.toString());
    }

    public void testGetUidDescriptorNull() throws Exception {
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        assertNull(registry.getUid(null, null));
    }

    public void testGetUidDescriptorDoesntExist() throws Exception {
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        assertNull(registry.getUid(null, DefDescriptorImpl.getInstance("unknown:soldier", ComponentDef.class)));
    }

    public void testGetUidLocalDef() throws Exception {
        DefDescriptor<ComponentDef> desc = Aura.getDefinitionService().getDefDescriptor("twiddledee:twiddledumb", ComponentDef.class);
        ComponentDef def = Mockito.mock(ComponentDef.class);
        Mockito.when(def.getDescriptor()).thenReturn(desc);

        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        registry.addLocalDef(def);
        assertNotNull(registry.getUid(null, def.getDescriptor()));
    }

    public void testGetUidSameAcrossInstances() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        MasterDefRegistryImplOverride registry1 = getDefRegistry(false);
        String uid1 = registry1.getUid(null, cmpDesc);
        MasterDefRegistryImplOverride registry2 = getDefRegistry(false);
        String uid2 = registry2.getUid(null, cmpDesc);
        assertEquals("Expected same UID for def from separate registry instances", uid1, uid2);
    }

    public void testGetUidUnique() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc1 = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        DefDescriptor<ComponentDef> cmpDesc2 = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        String uid1 = registry.getUid(null, cmpDesc1);
        String uid2 = registry.getUid(null, cmpDesc2);
        assertTrue("Components with same markup and dependencies should have different UIDs", !uid1.equals(uid2));
    }

    public void testGetUidCachedForChangedDefinition() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        String uid = registry.getUid(null, cmpDesc);

        // UID cached for current registry
        registry.getSource(cmpDesc).addOrUpdate(
                "<aura:component><aura:attribute name='str' type='String'/></aura:component>");
        String uidNew = registry.getUid(null, cmpDesc);
        assertEquals("UID not cached", uid, uidNew);

        // UID not cached for new registry
        MasterDefRegistryImplOverride registryNext = getDefRegistry(false);
        String uidNext = registryNext.getUid(null, cmpDesc);
        assertFalse("UID not cached in new registry", uid.equals(uidNext));
    }

    public void testGetUidCachedForRemovedDefinition() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>", null);
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        String uid = registry.getUid(null, cmpDesc);

        // UID cached for current registry
        getAuraTestingUtil().removeSource(cmpDesc);
        String uidNew = registry.getUid(null, cmpDesc);
        assertEquals("UID not cached", uid, uidNew);

        // UID not cached for new registry
        MasterDefRegistryImplOverride registryNext = getDefRegistry(false);
        String uidNext = registryNext.getUid(null, cmpDesc);
        assertNull("UID cached in new registry", uidNext);
    }

    public void testGetUidForQuickFixException() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component><unknown:component/></aura:component>", null);
        MasterDefRegistryImplOverride registry = getDefRegistry(true);
        try {
            registry.getUid(null, cmpDesc);
            fail("Expected DefinitionNotFoundException");
        } catch (DefinitionNotFoundException e) {
            checkExceptionStart(e, null, "No COMPONENT named markup://unknown:component found");
        }
        Mockito.verify(registry, Mockito.times(1)).compileDE(Mockito.eq(cmpDesc));

        // another request for getUid will not re-compile
        Mockito.reset(registry);
        try {
            registry.getUid(null, cmpDesc);
            fail("Expected DefinitionNotFoundException");
        } catch (DefinitionNotFoundException e) {
            checkExceptionStart(e, null, "No COMPONENT named markup://unknown:component found");
        }
        Mockito.verify(registry, Mockito.times(0)).compileDE(Mockito.eq(cmpDesc));
    }

    public void testGetUidForNonQuickFixException() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component invalidAttribute=''/>", null);
        MasterDefRegistryImplOverride registry = getDefRegistry(true);
        try {
            registry.getUid(null, cmpDesc);
            fail("Expected InvalidDefinitionException");
        } catch (Throwable t) {
            checkExceptionContains(t, InvalidDefinitionException.class,
                    "Invalid attribute \"invalidAttribute\"");
        }

        // another request for getUid will not re-compile again
        Mockito.reset(registry);
        try {
            registry.getUid(null, cmpDesc);
            fail("Expected InvalidDefinitionException");
        } catch (Throwable e) {
            checkExceptionContains(e, InvalidDefinitionException.class,
                    "Invalid attribute \"invalidAttribute\"");
        }
        Mockito.verify(registry, Mockito.times(0)).compileDE(Mockito.eq(cmpDesc));
    }

    public void testCompileDef() throws Exception {
        // create test component with 2 explicit dependencies
        DefDescriptor<ComponentDef> cmpDesc1 = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        DefDescriptor<ComponentDef> cmpDesc2 = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(
                ComponentDef.class,
                String.format(baseComponentTag, "",
                        String.format("<%s/><%s/>", cmpDesc1.getDescriptorName(), cmpDesc2.getDescriptorName())));

        // spy on MDR
        final MasterDefRegistryImplOverride registry = getDefRegistry(true);
        spyOnDefs(registry);

        // get def UID to trigger compileDef, etc.
        String uid = registry.getUid(null, cmpDesc);
        assertNotNull(uid);
        ComponentDef def = registry.getDef(cmpDesc);
        assertNotNull(def);
        Mockito.verify(registry, Mockito.times(1)).compileDE(Mockito.eq(cmpDesc));
        assertCompiledDef(def);

        // check all dependencies
        MockUtil mockUtil = new MockUtil();
        Set<DefDescriptor<?>> dependencies = registry.getDependencies(uid);
        for (DefDescriptor<?> dep : dependencies) {
            Definition depDef = registry.getDef(dep);
            if (mockUtil.isMock(depDef)) {
                // why not controllers?
                if (dep.getDefType().equals(DefType.CONTROLLER)) {
                    continue;
                }
                assertCompiledDef(depDef);
            }
        }
    }

    public void testCompileDefLocalDef() throws Exception {
        // build a mock def
        String descName = String.format("%s:ghost", System.nanoTime());
        ComponentDef def = Mockito.mock(ComponentDef.class);

        Mockito.doReturn(DefDescriptorImpl.getInstance(descName, ComponentDef.class)).when(def).getDescriptor();

        // spy on MDR's registries to spy on defs
        final MasterDefRegistryImplOverride registry = getDefRegistry(true);
        spyOnDefs(registry);
        registry.addLocalDef(def);

        // get def UID to trigger compileDef, etc.
        String uid = registry.getUid(null, def.getDescriptor());
        assertNotNull(uid);
        Mockito.verify(registry, Mockito.times(1)).compileDE(Mockito.eq(def.getDescriptor()));
        Mockito.doReturn(true).when(def).isValid();
        assertCompiledDef(def);

        // check all dependencies
        MockUtil mockUtil = new MockUtil();
        Set<DefDescriptor<?>> dependencies = registry.getDependencies(uid);
        for (DefDescriptor<?> dep : dependencies) {
            Definition depDef = registry.getDef(dep);
            if (mockUtil.isMock(depDef)) {
                assertCompiledDef(depDef);
            }
        }
    }

    public void testCompileDefOnlyOnce() throws Exception {
        // getDef on registry should compile the def
        String cmpContent = "<aura:component/>";
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class, cmpContent);
        MasterDefRegistryImplOverride registry = getDefRegistry(true);
        registry.getDef(cmpDesc);
        Mockito.verify(registry, Mockito.times(1)).compileDE(Mockito.eq(cmpDesc));

        // another getDef on same registry should not re-compile the def
        Mockito.reset(registry);
        assertNotNull(registry.getDef(cmpDesc));
        Mockito.verify(registry, Mockito.times(0)).compileDE(Mockito.eq(cmpDesc));

        // another getDef on other registry instance should now compile zero additional times
        registry = getDefRegistry(true);
        assertNotNull(registry.getDef(cmpDesc));
        Mockito.verify(registry, Mockito.times(0)).compileDE(Mockito.eq(cmpDesc));
    }

    public void testGetDefDescriptorNull() throws Exception {
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        assertNull(registry.getDef(null));
    }

    public void testGetDefDescriptorDoesntExist() throws Exception {
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        assertNull(registry.getDef(DefDescriptorImpl.getInstance("unknown:soldier", ComponentDef.class)));
    }

    public void testGetDefCachedForChangedDefinition() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        ComponentDef def = registry.getDef(cmpDesc);
        assertNull(def.getAttributeDef("str"));

        // Definition cached for current registry
        registry.getSource(cmpDesc).addOrUpdate(
                "<aura:component><aura:attribute name='str' type='String'/></aura:component>");
        ComponentDef defNew = registry.getDef(cmpDesc);
        assertNull(defNew.getAttributeDef("str"));

        // Definition not cached for new registry
        MasterDefRegistryImplOverride registryNext = getDefRegistry(false);
        ComponentDef defNext = registryNext.getDef(cmpDesc);
        assertNotNull(defNext.getAttributeDef("str"));
    }

    public void testGetDefCachedForRemovedDefinition() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>", null);
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        ComponentDef def = registry.getDef(cmpDesc);
        assertNotNull(def);

        // Definition cached for current registry
        getAuraTestingUtil().removeSource(cmpDesc);
        ComponentDef defNew = registry.getDef(cmpDesc);
        assertNotNull(defNew);

        // Definition not cached for new registry
        MasterDefRegistryImplOverride registryNext = getDefRegistry(false);
        ComponentDef defNext = registryNext.getDef(cmpDesc);
        assertNull(defNext);
    }

    public void testGetRawDefReturnsNullForRemovedDefinition() throws Exception {
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>", null);
        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        ComponentDef def = registry.getRawDef(cmpDesc);
        assertNotNull(def);

        getAuraTestingUtil().removeSource(cmpDesc);
        def = registry.getRawDef(cmpDesc);
        assertNull(def);
    }

    /**
     * Circular dependencies case 1: A has inner component B, and B has an explicit dependency on A (via aura:dependency
     * tag)
     */
    public void testCircularDependenciesInnerCmp() throws Exception {
        DefDescriptor<ComponentDef> cmpDescA = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        DefDescriptor<ComponentDef> cmpDescB = addSourceAutoCleanup(
                ComponentDef.class,
                String.format("<aura:component><aura:dependency resource=\"%s\"/></aura:component>",
                        cmpDescA.getQualifiedName()));
        updateStringSource(cmpDescA,
                String.format("<aura:component><%s/></aura:component>", cmpDescB.getDescriptorName()));
        // Circular dependency cases should result in identical dependencies
        assertIdenticalDependencies(cmpDescA, cmpDescB);
    }

    /**
     * Circular dependencies case 2: D extends C, and C has explicit dependency on D (via aura:dependency tag)
     */
    public void testCircularDependenciesExtendsCmp() throws Exception {
        DefDescriptor<ComponentDef> cmpDescC = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        DefDescriptor<ComponentDef> cmpDescD = addSourceAutoCleanup(ComponentDef.class,
                String.format("<aura:component extends=\"%s\"/>", cmpDescC.getDescriptorName()));
        updateStringSource(cmpDescC, String.format(
                "<aura:component extensible=\"true\"><aura:dependency resource=\"%s\"/></aura:component>",
                cmpDescD.getQualifiedName()));
        // Circular dependency cases should result in identical dependencies
        assertIdenticalDependencies(cmpDescC, cmpDescD);
    }

    /**
     * Circular dependencies case 3: E has dependency on F, and F has dependency on E (both through aura:dependency tag)
     */
    public void testCircularDependenciesDepTag() throws Exception {
        DefDescriptor<ComponentDef> cmpDescE = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        DefDescriptor<ComponentDef> cmpDescF = addSourceAutoCleanup(
                ComponentDef.class,
                String.format("<aura:component><aura:dependency resource=\"%s\"/></aura:component>",
                        cmpDescE.getQualifiedName()));
        updateStringSource(
                cmpDescE,
                String.format("<aura:component><aura:dependency resource=\"%s\"/></aura:component>",
                        cmpDescF.getQualifiedName()));
        // Circular dependency cases should result in identical dependencies
        assertIdenticalDependencies(cmpDescE, cmpDescF);
    }

    /**
     * Verify correct dependencies are attached to a component.
     */
    public void testGetDependencies() throws Exception {
        DefDescriptor<ComponentDef> depCmpDesc1 = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        DefDescriptor<ComponentDef> depCmpDesc2 = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        DefDescriptor<ComponentDef> cmpDesc1 = addSourceAutoCleanup(ComponentDef.class, "<aura:component/>");
        // Manually add dependency to inner component
        DefDescriptor<ComponentDef> cmpDesc2 = addSourceAutoCleanup(ComponentDef.class,
                "<aura:component><aura:dependency resource=\"" + depCmpDesc1.getQualifiedName()
                        + "\"/></aura:component>");
        DefDescriptor<ComponentDef> cmpDesc = addSourceAutoCleanup(
                ComponentDef.class,
                String.format(
                        baseComponentTag,
                        "",
                        String.format("<aura:dependency resource=\"" + depCmpDesc2.getQualifiedName()
                                + "\"/><%s/><%s/>", cmpDesc1.getDescriptorName(), cmpDesc2.getDescriptorName())));

        MasterDefRegistryImplOverride registry = getDefRegistry(false);
        String uid = registry.getUid(null, cmpDesc);
        Set<DefDescriptor<?>> deps = registry.getDependencies(uid);
        assertTrue("Component should have dependency on aura:component by default",
                checkDependenciesContains(deps, "markup://aura:component"));
        assertTrue("Component should have dependency on aura:rootComponent by default",
                checkDependenciesContains(deps, "markup://aura:rootComponent"));
        assertTrue("Component should not have a dependency on aura:application",
                !checkDependenciesContains(deps, "markup://aura:application"));
        assertTrue("No dependency on self found in Component",
                checkDependenciesContains(deps, cmpDesc.getQualifiedName()));
        assertTrue("Dependency on inner component not found",
                checkDependenciesContains(deps, cmpDesc1.getQualifiedName()));
        assertTrue("Dependency on inner component not found",
                checkDependenciesContains(deps, cmpDesc2.getQualifiedName()));
        assertTrue("Explicitly declared dependency on inner component not found",
                checkDependenciesContains(deps, depCmpDesc1.getQualifiedName()));
        assertTrue("Explicitly declared dependency on top level component not found",
                checkDependenciesContains(deps, depCmpDesc2.getQualifiedName()));
    }

    /**
     * Verify caches are cleared after a source change to a component file. In this case only the component def itself
     * should be cleared from the cache.
     */
    @ThreadHostileTest("requires cache to remain stable")
    public void testInvalidateCacheCmpFile() throws Exception {
        MasterDefRegistryImplOverride mdr = getDefRegistry(false);

        Map<DefType, DefDescriptor<?>> defs = addDefsToCaches(mdr);
        DefDescriptor<?> cmpDef = defs.get(DefType.COMPONENT);
        Aura.getCachingService().notifyDependentSourceChange(Collections.<WeakReference<SourceListener>> emptySet(),
                cmpDef, SourceListener.SourceMonitorEvent.CHANGED, null);

        assertFalse("ComponentDef not cleared from cache", isInDefsCache(defs.get(DefType.COMPONENT), mdr));
        assertTrue("ControllerDef in same bundle as cmp should not be cleared from cache",
                isInDefsCache(defs.get(DefType.CONTROLLER), mdr));
    }

    /**
     * Create a set of DefDescriptors and add them to the MDR caches by calling getDef() on them.
     *
     * @return List of DefDescriptors that have been added to the mdr caches.
     */
    private Map<DefType, DefDescriptor<?>> addDefsToCaches(MasterDefRegistryImpl mdr) throws Exception {
        DefDescriptor<ComponentDef> cmpDef = DefDescriptorImpl.getInstance("test:test_button",
                ComponentDef.class);
        DefDescriptor<ControllerDef> cmpControllerDef = DefDescriptorImpl.getInstance(
                "js://test.test_button", ControllerDef.class);
        DefDescriptor<RendererDef> otherNamespaceDef = DefDescriptorImpl.getInstance(
                "js://gvpTest.labelProvider", RendererDef.class);
        DefDescriptor<ApplicationDef> appDef = DefDescriptorImpl.getInstance("test:basicCspTest", ApplicationDef.class);


        Map<DefType, DefDescriptor<?>> map = new HashMap<>();
        map.put(DefType.COMPONENT, cmpDef);
        map.put(DefType.CONTROLLER, cmpControllerDef);
        map.put(DefType.RENDERER, otherNamespaceDef);
        map.put(DefType.APPLICATION, appDef);

        for (DefType defType : map.keySet()) {
            DefDescriptor<?> dd = map.get(defType);
            dd.getDef();
        }

        return map;
    }

    private boolean isInDescriptorFilterCache(DescriptorFilter filter, Set<DefDescriptor<?>> results,
            MasterDefRegistryImpl mdr) throws Exception {
        // taking the long road in determining what is in the cache because the current key implementation for
        // the descriptor cache is difficult to recreate.
        Cache<String, Set<DefDescriptor<?>>> cache = AuraPrivateAccessor.get(mdr, "descriptorFilterCache");
        for (String key : cache.getKeySet()) {
            if (key.startsWith(filter.toString() + "|")) {
                return results.equals(cache.getIfPresent(key));
            }
        }
        return false;
    }

    /**
     * Check to ensure that a DescriptorFilter is not in the cache.
     */
    private boolean notInDescriptorFilterCache(DescriptorFilter filter, MasterDefRegistryImpl mdr) throws Exception {
        Cache<String, Set<DefDescriptor<?>>> cache = AuraPrivateAccessor.get(mdr, "descriptorFilterCache");
        for (String key : cache.getKeySet()) {
            if (key.startsWith(filter.toString() + "|")) {
                return false;
            }
        }
        return true;
    }

    private boolean isInDepsCache(DefDescriptor<?> dd, MasterDefRegistryImpl mdr) throws Exception {
        Cache<String, ?> cache = AuraPrivateAccessor.get(mdr, "depsCache");
        String ddKey = dd.getDescriptorName().toLowerCase();
        for (String key : cache.getKeySet()) {
            if (key.endsWith(ddKey)) {
                return cache.getIfPresent(key) != null;
            }
        }
        return false;
    }

    private boolean isInDefsCache(DefDescriptor<?> dd, MasterDefRegistryImpl mdr) throws Exception {
        Cache<DefDescriptor<?>, Optional<? extends Definition>> cache = AuraPrivateAccessor.get(mdr, "defsCache");
        return null != cache.getIfPresent(dd);
    }

    private boolean isInExistsCache(DefDescriptor<?> dd, MasterDefRegistryImpl mdr) throws Exception {
        Cache<DefDescriptor<?>, Boolean> cache = AuraPrivateAccessor.get(mdr, "existsCache");
        return Boolean.TRUE == cache.getIfPresent(dd);
    }

    private <D extends Definition> void callAssertAccess(MasterDefRegistryImpl mdr,
            DefDescriptor<?> referencingDescriptor, D def, Cache<String, String> accessCheckCache)
            throws Exception {
        Method assertAccess = mdr.getClass().getMethod("assertAccess", DefDescriptor.class,
                Definition.class, Cache.class);
        assertAccess.setAccessible(true);
        assertAccess.invoke(mdr, referencingDescriptor, def, accessCheckCache);
    }

    /**
     * Verify basic functionality of MasterDefRegistryImpl.getClientLibraries. The same methods are test in
     * ClientLibraryServiceImplTest, where we use ClientLibraryService.getUrls()
     *
     * @throws Exception
     */
    public void testGetClientLibraries() throws Exception {
        MasterDefRegistry mdr = getAuraMDR();
        List<ClientLibraryDef> libDefs = mdr.getClientLibraries(null);
        assertNull(libDefs);

        DefDescriptor<ApplicationDef> appDesc = Aura.getDefinitionService().getDefDescriptor(
                "clientLibraryTest:testDependencies", ApplicationDef.class);
        AuraContext cntx = Aura.getContextService().getCurrentContext();
        cntx.setApplicationDescriptor(appDesc);
        Aura.getDefinitionService().updateLoaded(appDesc);

        libDefs = mdr.getClientLibraries(cntx.getUid(appDesc));

        // 13 from clientLibraryTest:testDependencies and its dependencies + 1 from aura:component
        // Update this number when you add new aura:clientLibrary tags to these components
        assertEquals(13, libDefs.size());
    }

    public void testAssertAccess_IfGlobalAccessThenPassesCheck() throws Exception {
        when(globalDef.getAccess()).thenReturn(defAccess);
        when(defAccess.isGlobal()).thenReturn(true);
        MasterDefRegistry mdr = getAuraMDR();
        mdr.assertAccess(null, globalDef);

        verify(globalDef).getAccess();
        verify(defAccess).isGlobal();
    }

    public void testAssertAccess_IfReferencedByUnsecuredPrefixThenPassesCheck() throws Exception {
        when(globalDef.getAccess()).thenReturn(defAccess);
        when(defAccess.isGlobal()).thenReturn(false);
        when(defAccess.requiresAuthentication()).thenReturn(true);
        when(referencingDesc.getPrefix()).thenReturn("aura");
        MasterDefRegistry mdr = getAuraMDR();
        mdr.assertAccess(referencingDesc, globalDef);

        verify(referencingDesc).getPrefix();
    }

    /**
     * Verify that if access cache has a reason to block access, then MDR throws NoAccessException.
     *
     * @throws Exception
     */
    public void testAssertAccess_UsesCachedValueIfPresent_BlockAccess() throws Exception {
        DefDescriptor<ComponentDef> desc = addSourceAutoCleanup(ComponentDef.class,
                String.format(baseComponentTag, "", ""));
        when(mockAccessCheckCache.getIfPresent(anyString())).thenReturn("Error");
        MasterDefRegistryImpl mdr = (MasterDefRegistryImpl) getAuraMDR();
        Throwable ex = null;
        try {
            callAssertAccess(mdr, null, desc.getDef(), mockAccessCheckCache);
        } catch (InvocationTargetException ite) {
            ex = ite.getTargetException();
        } catch (Exception e) {
            ex = e;
        }
        if (ex == null) {
            fail("Expected NoAccessException because accessCache has reason to block def");
        }
        this.assertExceptionMessageStartsWith(ex, NoAccessException.class, "Error");
        verify(mockAccessCheckCache).getIfPresent(anyString());
    }

    /**
     * Verify that if access cache doesn't have any message to block access, then access checks passes through.
     *
     * @throws Exception
     */
    public void testAssertAccess_UsesCachedValueIfPresent_AllowAccess() throws Exception {
        DefDescriptor<ComponentDef> desc = addSourceAutoCleanup(ComponentDef.class,
                String.format(baseComponentTag, "", ""));
        when(mockAccessCheckCache.getIfPresent(anyString())).thenReturn("");
        MasterDefRegistryImpl mdr = (MasterDefRegistryImpl) getAuraMDR();
        callAssertAccess(mdr, null, desc.getDef(), mockAccessCheckCache);

        verify(mockAccessCheckCache).getIfPresent(anyString());
    }

    public void testAssertAccess_StoreAccessInfoInCacheIfNotPresent() throws Exception {
        DefDescriptor<ComponentDef> desc = getAuraTestingUtil().addSourceAutoCleanup(ComponentDef.class,
                String.format(baseComponentTag, "", ""), StringSourceLoader.DEFAULT_CUSTOM_NAMESPACE + ":testComp",
                false);
        when(mockAccessCheckCache.getIfPresent(anyString())).thenReturn(null);

        MasterDefRegistryImpl mdr = (MasterDefRegistryImpl) getAuraMDR();
        callAssertAccess(mdr, desc, desc.getDef(), mockAccessCheckCache);

        verify(mockAccessCheckCache).put(anyString(), anyString());

        callAssertAccess(mdr, desc, desc.getDef(), mockAccessCheckCache);
        verify(mockAccessCheckCache, times(2)).getIfPresent(anyString());
    }

    public void testExistsCache() throws Exception {
        ConfigAdapter configAdapter = Aura.getConfigAdapter();
        MasterDefRegistry mdr = getAuraMDR();
        MasterDefRegistryImpl mdri = (MasterDefRegistryImpl) mdr;
        Map<DefType, DefDescriptor<?>> defs = addDefsToCaches(mdri);
        Map<DefType, DefDescriptor<?>> nonPrivDefs = addNonPriveledgedDefsToMDR(mdri);
        for (DefDescriptor<?> dd : defs.values()) {
            assertTrue(dd + " should exist.", dd.exists());
        }
        for (DefDescriptor<?> dd : nonPrivDefs.values()) {
            assertTrue(dd + " should exist.", dd.exists());
        }

        DefDescriptor<?> rendererDef = defs.get(DefType.RENDERER);
        DefDescriptor<?> appDef = defs.get(DefType.APPLICATION);
        DefDescriptor<?> controllerDef = defs.get(DefType.CONTROLLER);
        DefDescriptor<?> cmpDef = defs.get(DefType.COMPONENT);

        DefDescriptor<?> npRendererDef = nonPrivDefs.get(DefType.RENDERER);
        DefDescriptor<?> nsAppDef = nonPrivDefs.get(DefType.APPLICATION);
        DefDescriptor<?> nsControllerDef = nonPrivDefs.get(DefType.CONTROLLER);
        DefDescriptor<?> nsCmpDef = nonPrivDefs.get(DefType.COMPONENT);

        // only picking 3 defs to test the ns as they are mostly dupes
        assertTrue(rendererDef.getNamespace() + "  should have been isPriveleged",
                configAdapter.isPrivilegedNamespace(rendererDef.getNamespace()));

        assertFalse(npRendererDef.getNamespace() + "  should not have been isPriveleged",
                configAdapter.isPrivilegedNamespace(npRendererDef.getNamespace()));

        MasterDefRegistry mdr2 = restartContextGetNewMDR();
        MasterDefRegistryImpl mdri2 = (MasterDefRegistryImpl) mdr2;

        // objects wont be in eists cache yet, just defsCache, need to call exists to prime exists cache

        for (DefDescriptor<?> dd : defs.values()) {
            assertTrue(dd + " should exist.", dd.exists());
        }
        assertTrue("RendererDef is in cache", isInExistsCache(rendererDef, mdri2));
        assertTrue("app is in cache", isInExistsCache(appDef, mdri2));
        assertTrue("controller is in cache", isInExistsCache(controllerDef, mdri2));
        assertTrue("cmp is in cache", isInExistsCache(cmpDef, mdri2));

        assertFalse("npRendererDef is notin cache", isInExistsCache(npRendererDef, mdri2));
        assertFalse("nsApp is not in cache", isInExistsCache(nsAppDef, mdri2));
        assertFalse("nsController is not in cache", isInExistsCache(nsControllerDef, mdri2));
        assertFalse("nsCmp is not in cache", isInExistsCache(nsCmpDef, mdri2));

        MasterDefRegistry mdr3 = restartContextGetNewMDR();
        MasterDefRegistryImpl mdri3 = (MasterDefRegistryImpl) mdr3;

        assertTrue("RendererDef is in cache", isInExistsCache(rendererDef, mdri3));
        assertTrue("app is in cache", isInExistsCache(appDef, mdri3));
        assertTrue("controller is in cache", isInExistsCache(controllerDef, mdri3));
        assertTrue("cmp is in cache", isInExistsCache(cmpDef, mdri3));

        assertFalse("npRendererDef is notin cache", isInExistsCache(npRendererDef, mdri3));
        assertFalse("nsApp is not in cache", isInExistsCache(nsAppDef, mdri3));
        assertFalse("nsController is not in cache", isInExistsCache(nsControllerDef, mdri3));
        assertFalse("nsCmp is not in cache", isInExistsCache(nsCmpDef, mdri3));
    }

    public void testDefsCache() throws Exception {
        ConfigAdapter configAdapter = Aura.getConfigAdapter();
        MasterDefRegistry mdr = getAuraMDR();
        MasterDefRegistryImpl mdri = (MasterDefRegistryImpl) mdr;
        Map<DefType, DefDescriptor<?>> defs = addDefsToCaches(mdri);
        Map<DefType, DefDescriptor<?>> nonPrivDefs = addNonPriveledgedDefsToMDR(mdri);

        DefDescriptor<?> rendererDef = defs.get(DefType.RENDERER);
        DefDescriptor<?> appDef = defs.get(DefType.APPLICATION);
        DefDescriptor<?> controllerDef = defs.get(DefType.CONTROLLER);
        DefDescriptor<?> cmpDef = defs.get(DefType.COMPONENT);

        DefDescriptor<?> npRendererDef = nonPrivDefs.get(DefType.RENDERER);
        DefDescriptor<?> nsAppDef = nonPrivDefs.get(DefType.APPLICATION);
        DefDescriptor<?> nsControllerDef = nonPrivDefs.get(DefType.CONTROLLER);
        DefDescriptor<?> nsCmpDef = nonPrivDefs.get(DefType.COMPONENT);

        // only picking 3 defs to test the ns as they are mostly dupes
        assertTrue(appDef.getNamespace() + "  should have been isPriveleged",
                configAdapter.isPrivilegedNamespace(appDef.getNamespace()));
        assertTrue(rendererDef.getNamespace() + "  should have been isPriveleged",
                configAdapter.isPrivilegedNamespace(rendererDef.getNamespace()));

        assertFalse(nsAppDef.getNamespace() + "  should not have been isPriveleged",
                configAdapter.isPrivilegedNamespace(nsAppDef.getNamespace()));
        assertFalse(npRendererDef.getNamespace() + "  should not have been isPriveleged",
                configAdapter.isPrivilegedNamespace(npRendererDef.getNamespace()));

        assertTrue("RendererDef is in cache", isInDefsCache(rendererDef, mdri));
        assertTrue("app is in cache", isInDefsCache(appDef, mdri));
        assertTrue("controller is in cache", isInDefsCache(controllerDef, mdri));
        assertTrue("cmp is in cache", isInDefsCache(cmpDef, mdri));

        assertFalse("npRendererDef is not in cache", isInDefsCache(npRendererDef, mdri));
        assertFalse("nsApp is not in cache", isInDefsCache(nsAppDef, mdri));
        assertFalse("nsController is not in cache", isInDefsCache(nsControllerDef, mdri));
        assertFalse("nsCmp is not in cache", isInDefsCache(nsCmpDef, mdri));

        MasterDefRegistry mdr2 = restartContextGetNewMDR();
        MasterDefRegistryImpl mdri2 = (MasterDefRegistryImpl) mdr2;

        assertTrue("RendererDef is in cache", isInDefsCache(rendererDef, mdri2));
        assertTrue("app is in cache", isInDefsCache(appDef, mdri2));
        assertTrue("controller is in cache", isInDefsCache(controllerDef, mdri2));
        assertTrue("cmp is in cache", isInDefsCache(cmpDef, mdri2));

        assertFalse("npRendererDef is notin cache", isInDefsCache(npRendererDef, mdri2));
        assertFalse("nsApp is not in cache", isInDefsCache(nsAppDef, mdri2));
        assertFalse("nsController is not in cache", isInDefsCache(nsControllerDef, mdri2));
        assertFalse("nsCmp is not in cache", isInDefsCache(nsCmpDef, mdri2));
    }

    public void testDescriptorFilterCache() throws Exception {
        ConfigAdapter configAdapter = Aura.getConfigAdapter();
        MasterDefRegistry mdr = getAuraMDR();
        MasterDefRegistryImpl mdri = (MasterDefRegistryImpl) mdr;
        Map<DefType, DefDescriptor<?>> defs = addDefsToCaches(mdri);
        Map<DefType, DefDescriptor<?>> nonPrivDefs = addNonPriveledgedDefsToMDR(mdri);

        DefDescriptor<?> rendererDef = defs.get(DefType.RENDERER);

        DefDescriptor<?> npRendererDef = nonPrivDefs.get(DefType.RENDERER);

        // only picking 3 defs to test the ns as they are mostly dupes
        assertTrue(rendererDef.getNamespace() + "  should have been isPriveleged",
                configAdapter.isPrivilegedNamespace(rendererDef.getNamespace()));

        assertFalse(npRendererDef.getNamespace() + "  should not have been isPriveleged",
                configAdapter.isPrivilegedNamespace(npRendererDef.getNamespace()));

        DescriptorFilter filter = new DescriptorFilter("*://test:*");
        Set<DefDescriptor<?>> results = mdr.find(filter);
        assertTrue("results should be cached", isInDescriptorFilterCache(filter, results, mdri));
        DescriptorFilter filter2 = new DescriptorFilter("*://gvpTest:*");
        Set<DefDescriptor<?>> results2 = mdr.find(filter2);
        assertTrue("results2 should be cached", isInDescriptorFilterCache(filter2, results2, mdri));

        DescriptorFilter filter3 = new DescriptorFilter("*://cstring:*");
        Set<DefDescriptor<?>> results3 = mdr.find(filter3);
        assertFalse("results3 should not be cached", isInDescriptorFilterCache(filter3, results3, mdri));
        DescriptorFilter filter4 = new DescriptorFilter("*://cstring1:*");
        Set<DefDescriptor<?>> results4 = mdr.find(filter4);
        assertFalse("results4 should be cached", isInDescriptorFilterCache(filter4, results4, mdri));

        DescriptorFilter filter5 = new DescriptorFilter("*://*:*");
        Set<DefDescriptor<?>> results5 = mdr.find(filter5);
        assertFalse("results5 should not be cached", isInDescriptorFilterCache(filter5, results5, mdri));
        // DescriptorFilter filter6 = new DescriptorFilter("*://*test:*");
        // Set<DefDescriptor<?>> results6 = mdr.find(filter6);
        // assertFalse("results6 should be cached", isInDescriptorFilterCache(filter6, results6, mdri));

        MasterDefRegistry mdr2 = restartContextGetNewMDR();
        MasterDefRegistryImpl mdri2 = (MasterDefRegistryImpl) mdr2;
        assertTrue("results should still be cached", isInDescriptorFilterCache(filter, results, mdri2));
        assertTrue("results2 should still be cached", isInDescriptorFilterCache(filter2, results2, mdri2));
        assertFalse("results3 should not be cached", isInDescriptorFilterCache(filter3, results3, mdri2));
        assertFalse("results4 should not be cached", isInDescriptorFilterCache(filter4, results4, mdri2));
        assertFalse("results5 should not be cached", isInDescriptorFilterCache(filter5, results5, mdri2));
        // assertFalse("results6 should not be cached", isInDescriptorFilterCache(filter6, results6, mdri2));
    }

    /**
     * Test a constant Descriptor filter.
     *
     * Preconditions: (1) Cacheable registry. (2) DescriptorFilter.isConstant() should be true.
     *
     * PostConditions: (1) registry.find() should _not_ be called. (2) We should get exactly one descriptor. (3) It
     * should contain the def descriptor. (4) There should be no cache.
     */
    @SuppressWarnings("unchecked")
    public <D extends Definition> void testDescriptorFilterConstant() throws Exception {
        DefRegistry<D> mockedRegistry = Mockito.mock(DefRegistry.class);
        DefDescriptor<D> dd = (DefDescriptor<D>) DefDescriptorImpl.getInstance("markup", "aura", "mocked", DefType.COMPONENT);
        Set<DefDescriptor<?>> findable = Sets.newHashSet();
        findable.add(dd);

        Mockito.when(mockedRegistry.getNamespaces()).thenReturn(Sets.newHashSet("aura"));
        Mockito.when(mockedRegistry.getPrefixes()).thenReturn(Sets.newHashSet("markup"));
        Mockito.when(mockedRegistry.getDefTypes()).thenReturn(Sets.newHashSet(DefType.COMPONENT));
        Mockito.when(mockedRegistry.find((DescriptorFilter) Mockito.any())).thenReturn(findable);
        Mockito.when(mockedRegistry.hasFind()).thenReturn(true);
        Mockito.when(mockedRegistry.exists(dd)).thenReturn(true);
        MasterDefRegistryImpl mdr = new MasterDefRegistryImpl(mockedRegistry);

        DescriptorFilter df = new DescriptorFilter("aura:mocked", DefType.COMPONENT);
        Set<DefDescriptor<?>> result = mdr.find(df);
        assertEquals("Resulting set should contain exactly 1 item", 1, result.size());
        assertTrue("Resulting set should contain our descriptor", result.contains(dd));
        Mockito.verify(mockedRegistry, Mockito.never()).find((DescriptorFilter) Mockito.any());
        assertTrue("No caching for constant filters", notInDescriptorFilterCache(df, mdr));
    }

    /**
     * Test a wildcard name Descriptor filter (cacheable).
     *
     * Preconditions: (1) Cacheable registry. (2) DescriptorFilter.isConstant() should be true.
     *
     * PostConditions: (1) registry.find() should be called. (2) We should get exactly one descriptor. (3) It should
     * contain the def descriptor. (4) There should be a cache.
     */
    public void testDescriptorFilterNameWildcardWithCache() throws Exception {
        DefRegistry<?> mockedRegistry = Mockito.mock(DefRegistry.class);
        DefDescriptor<?> dd = DefDescriptorImpl.getInstance("markup", "aura", "mocked", DefType.COMPONENT);
        Set<DefDescriptor<?>> findable = Sets.newHashSet();
        findable.add(dd);

        Mockito.when(mockedRegistry.getNamespaces()).thenReturn(Sets.newHashSet("aura"));
        Mockito.when(mockedRegistry.getPrefixes()).thenReturn(Sets.newHashSet("markup"));
        Mockito.when(mockedRegistry.getDefTypes()).thenReturn(Sets.newHashSet(DefType.COMPONENT));
        Mockito.when(mockedRegistry.find((DescriptorFilter) Mockito.any())).thenReturn(findable);
        Mockito.when(mockedRegistry.hasFind()).thenReturn(true);
        Mockito.when(mockedRegistry.isCacheable()).thenReturn(true);

        MasterDefRegistryImpl mdr = new MasterDefRegistryImpl(mockedRegistry);

        DescriptorFilter df = new DescriptorFilter("aura:*", DefType.COMPONENT);
        Set<DefDescriptor<?>> result = mdr.find(df);
        assertEquals("Resulting set should contain exactly 1 item", 1, result.size());
        assertTrue("Resulting set should contain our descriptor", result.contains(dd));
        Mockito.verify(mockedRegistry).find(Mockito.eq(df));
        assertTrue("Caching for wildcard names", isInDescriptorFilterCache(df, findable, mdr));
    }

    /**
     * Test a wildcard name Descriptor filter (not cacheable).
     *
     * Preconditions: (1) Non-Cacheable registry. (2) DescriptorFilter.isConstant() should be true.
     *
     * PostConditions: (1) registry.find() should be called. (2) We should get exactly one descriptor. (3) It should
     * contain the def descriptor. (4) There should be no cache.
     */
    public void testDescriptorFilterNameWildcardWithoutCache() throws Exception {
        DefRegistry<?> mockedRegistry = Mockito.mock(DefRegistry.class);
        DefDescriptor<?> dd = DefDescriptorImpl.getInstance("markup", "aura", "mocked", DefType.COMPONENT);
        Set<DefDescriptor<?>> findable = Sets.newHashSet();
        findable.add(dd);

        Mockito.when(mockedRegistry.getNamespaces()).thenReturn(Sets.newHashSet("aura"));
        Mockito.when(mockedRegistry.getPrefixes()).thenReturn(Sets.newHashSet("markup"));
        Mockito.when(mockedRegistry.getDefTypes()).thenReturn(Sets.newHashSet(DefType.COMPONENT));
        Mockito.when(mockedRegistry.find((DescriptorFilter) Mockito.any())).thenReturn(findable);
        Mockito.when(mockedRegistry.hasFind()).thenReturn(true);
        Mockito.when(mockedRegistry.isCacheable()).thenReturn(false);

        MasterDefRegistryImpl mdr = new MasterDefRegistryImpl(mockedRegistry);

        DescriptorFilter df = new DescriptorFilter("aura:*", DefType.COMPONENT);
        Set<DefDescriptor<?>> result = mdr.find(df);
        assertEquals("Resulting set should contain exactly 1 item", 1, result.size());
        assertTrue("Resulting set should contain our descriptor", result.contains(dd));
        Mockito.verify(mockedRegistry).find(Mockito.eq(df));
        assertTrue("Caching for wildcard names", notInDescriptorFilterCache(df, mdr));
    }

    /**
     * Test a wildcard namespace Descriptor filter (cacheable).
     *
     * Preconditions: (1) Cacheable registry. (2) DescriptorFilter.isConstant() should be true.
     *
     * PostConditions: (1) registry.find() should be called. (2) We should get exactly one descriptor. (3) It should
     * contain the def descriptor. (4) There should be no cache.
     */
    public void testDescriptorFilterNamespaceWildcard() throws Exception {
        DefRegistry<?> mockedRegistry = Mockito.mock(DefRegistry.class);
        DefDescriptor<?> dd = DefDescriptorImpl.getInstance("markup", "aura", "mocked", DefType.COMPONENT);
        Set<DefDescriptor<?>> findable = Sets.newHashSet();
        findable.add(dd);

        Mockito.when(mockedRegistry.getNamespaces()).thenReturn(Sets.newHashSet("aura"));
        Mockito.when(mockedRegistry.getPrefixes()).thenReturn(Sets.newHashSet("markup"));
        Mockito.when(mockedRegistry.getDefTypes()).thenReturn(Sets.newHashSet(DefType.COMPONENT));
        Mockito.when(mockedRegistry.find((DescriptorFilter) Mockito.any())).thenReturn(findable);
        Mockito.when(mockedRegistry.hasFind()).thenReturn(true);
        Mockito.when(mockedRegistry.isCacheable()).thenReturn(true);

        MasterDefRegistryImpl mdr = new MasterDefRegistryImpl(mockedRegistry);

        DescriptorFilter df = new DescriptorFilter("*:mocked", DefType.COMPONENT);
        Set<DefDescriptor<?>> result = mdr.find(df);
        assertEquals("Resulting set should contain exactly 1 item", 1, result.size());
        assertTrue("Resulting set should contain our descriptor", result.contains(dd));
        Mockito.verify(mockedRegistry).find(Mockito.eq(df));
        assertTrue("No caching for wildcard namespaces", notInDescriptorFilterCache(df, mdr));
    }

    public void testDepsCache() throws Exception {
        String unprivilegedNamespace = getAuraTestingUtil().getNonce("alien");

        // in privileged namespace
        DefDescriptor<ComponentDef> privilegedCmp = getAuraTestingUtil().addSourceAutoCleanup(
                ComponentDef.class, String.format(baseComponentTag, "access='global'", ""), null, true);
        // in unprivileged namespace depending on privileged cmp
        DefDescriptor<ComponentDef> unprivilegedCmp = getAuraTestingUtil().addSourceAutoCleanup(
                DefDescriptorImpl.getInstance(String.format("markup://%s:cmp", unprivilegedNamespace),
                        ComponentDef.class),
                String.format(baseComponentTag, "access='global'",
                        String.format("<%s/>", privilegedCmp.getDescriptorName())),
                false);

        // in privileged namespace depending on unprivileged cmp
        DefDescriptor<ComponentDef> privilegedRoot = getAuraTestingUtil().addSourceAutoCleanup(
                ComponentDef.class,
                String.format(baseComponentTag, "access='global'",
                        String.format("<%s/>", unprivilegedCmp.getDescriptorName())),
                null, true);

        ConfigAdapter configAdapter = Aura.getConfigAdapter();
        assertTrue(configAdapter.isPrivilegedNamespace(privilegedCmp.getNamespace()));
        assertFalse(configAdapter.isPrivilegedNamespace(unprivilegedCmp.getNamespace()));
        assertTrue(configAdapter.isPrivilegedNamespace(privilegedRoot.getNamespace()));

        MasterDefRegistry mdr = Aura.getContextService().getCurrentContext().getDefRegistry();
        MasterDefRegistryImpl mdri = (MasterDefRegistryImpl) mdr;

        try {
            mdr.getDef(privilegedRoot);
            fail("Shouldn't be able to have a privileged cmp depend on an unprivileged cmp");
        } catch (Throwable t) {
            this.assertExceptionMessageStartsWith(t,
                    DefinitionNotFoundException.class, String.format(
                            "No COMPONENT named %s found",
                            unprivilegedCmp.getQualifiedName()));
        }

        mdr.getDef(unprivilegedCmp);
        assertFalse(isInDepsCache(privilegedCmp, mdri));
        assertFalse(isInDepsCache(unprivilegedCmp, mdri));
        assertFalse(isInDepsCache(privilegedRoot, mdri));


        mdr.getDef(privilegedCmp);
        assertTrue(isInDepsCache(privilegedCmp, mdri));
        assertFalse(isInDepsCache(unprivilegedCmp, mdri));
        assertFalse(isInDepsCache(privilegedRoot, mdri));

    }

    public void testJavaProtocolIsCached() throws Exception {
        DefDescriptor<ControllerDef> controllerDef = DefDescriptorImpl.getInstance(
                "java://org.auraframework.components.test.java.controller.TestController", ControllerDef.class);
        controllerDef.getDef();
        String prefix = controllerDef.getPrefix();
        assertEquals(prefix, "java");

        ConfigAdapter configAdapter = Aura.getConfigAdapter();
        assertFalse(configAdapter.isPrivilegedNamespace(controllerDef.getNamespace()));

        MasterDefRegistry mdr = Aura.getContextService().getCurrentContext().getDefRegistry();
        // mdr.getDef(controllerDef);
        MasterDefRegistryImpl mdri = (MasterDefRegistryImpl) mdr;
        assertTrue(isInDepsCache(controllerDef, mdri));
    }

    private MasterDefRegistry restartContextGetNewMDR() {
        // simulate new request
        MasterDefRegistry mdr = getAuraMDR();
        AuraContext ctx = Aura.getContextService().getCurrentContext();
        Mode mode = ctx.getMode();
        Format format = ctx.getFormat();
        Authentication access = ctx.getAccess();
        Aura.getContextService().endContext();

        Aura.getContextService().startContext(mode, format, access);
        MasterDefRegistry mdr2 = getAuraMDR();
        assertFalse("MasterDefRegistry should be different after restart of context", mdr == mdr2);
        return mdr2;
    }

    /**
     * Create a set of DefDescriptors and add them to the MDR caches by calling getDef() on them.
     *
     * @return List of DefDescriptors that have been added to the mdr caches.
     */
    private Map<DefType, DefDescriptor<?>> addNonPriveledgedDefsToMDR(MasterDefRegistryImpl mdr) throws Exception {
        DefDescriptor<ComponentDef> cmpDef = getAuraTestingUtil().addSourceAutoCleanup(ComponentDef.class,
                "<aura:component>"
                        + "<aura:attribute name='label' type='String'/>"
                        + "<aura:attribute name='class' type='String'/>"
                        + "<aura:registerevent name='press' type='test:test_press'/>"
                        + "<div onclick='{!c.press}' class='{!v.class}'>{!v.label}</div>"
                        + "</aura:component>", "cstring:test_button", false);
        DefDescriptor<ControllerDef> cmpControllerDef = getAuraTestingUtil().addSourceAutoCleanup(ControllerDef.class,
                "{    press : function(cmp, event){        cmp.getEvent('press').fire();    }}",
                "cstring.test_button", false);
        DefDescriptor<RendererDef> otherNamespaceDef = getAuraTestingUtil()
                .addSourceAutoCleanup(
                        RendererDef.class,
                        "({render: function(cmp) {"
                                + "cmp.getValue('v.simplevalue1').setValue($A.get('$Label' + '.Related_Lists' + '.task_mode_today', cmp));"
                                + "cmp.getValue('v.simplevalue2').setValue($A.get('$Label.DOESNT.EXIST', cmp));"
                                + "cmp.getValue('v.simplevalue3').setValue($A.get('$Label.Related_Lists.DOESNTEXIST', cmp));"
                                + "// Both section and name are required. This request will return undefined and no action is requested."
                                + "cmp.getValue('v.simplevalue4').setValue($A.get('$Label.DOESNTEXIST', cmp));"
                                + "// These requests are here to test that there are no multiple action requests for the same $Label"
                                + "// See LabelValueProviderUITest.java"
                                + "var tmt = $A.get('$Label.Related_Lists.task_mode_today', cmp);"
                                + "tmt = $A.get('$Label.Related_Lists.task_mode_today', cmp);"
                                + "tmt = $A.get('$Label.Related_Lists.task_mode_today', cmp);"
                                + "tmt = $A.get('$Label.Related_Lists.task_mode_today', cmp);"
                                + "tmt = $A.get('$Label.Related_Lists.task_mode_today', cmp);"
                                + "return this.superRender();"
                                + "}})",
                        "cstring1.labelProvider", false);
        DefDescriptor<ApplicationDef> app = getAuraTestingUtil().addSourceAutoCleanup(
                ApplicationDef.class,
                "<aura:application></aura:application>", "cstring1:blank", false);


        Map<DefType, DefDescriptor<?>> map = new HashMap<>();
        map.put(DefType.COMPONENT, cmpDef);
        map.put(DefType.CONTROLLER, cmpControllerDef);
        map.put(DefType.RENDERER, otherNamespaceDef);
        map.put(DefType.APPLICATION, app);

        for (DefType defType : map.keySet()) {
            DefDescriptor<?> dd = map.get(defType);
            dd.getDef();
        }

        return map;
    }

    private MasterDefRegistry getAuraMDR() {
        return Aura.getContextService().getCurrentContext().getDefRegistry();
    }

    /**
     * A fake type def, this could probably be a mock, the tricky part being isValid().
     */
    @SuppressWarnings("serial")
    private static class FakeTypeDef implements TypeDef {
        private boolean valid;
        DefDescriptor<TypeDef> desc;

        public FakeTypeDef(DefDescriptor<TypeDef> desc) {
            this.desc = desc;
        }

        @Override
        public void validateDefinition() throws QuickFixException {
        }

        @Override
        public void appendDependencies(Set<DefDescriptor<?>> dependencies) {
        }

        @Override
        public void validateReferences() throws QuickFixException {
        }

        @Override
        public void markValid() {
            valid = true;
        }

        @Override
        public boolean isValid() {
            return valid;
        }

        @Override
        public String getName() {
            return "name";
        }

        @Override
        public Location getLocation() {
            return null;
        }

        @Override
        public DefinitionAccess getAccess() {
            return null;
        }

        @Override
        public <D extends Definition> D getSubDefinition(SubDefDescriptor<D, ?> descriptor) {
            return null;
        }

        @Override
        public void retrieveLabels() throws QuickFixException {
        }

        @Override
        public String getAPIVersion() {
            return null;
        }

        @Override
        public String getDescription() {
            return "description";
        }

        @Override
        public String getOwnHash() {
            return "aaaa";
        }

        @Override
        public void appendSupers(Set<DefDescriptor<?>> supers) throws QuickFixException {
        }

        @Override
        public void serialize(Json json) throws IOException {

        }

        @Override
        public DefDescriptor<TypeDef> getDescriptor() {
            return desc;
        }

        @Override
        public Object valueOf(Object stringRep) {
            return null;
        }

        @Override
        public Object wrap(Object o) {
            return null;
        }

        @Override
        public Object getExternalType(String prefix) throws QuickFixException {
            return null;
        }

        @Override
        public Object initialize(Object config, BaseComponent<?, ?> valueProvider) throws QuickFixException {
            return null;
        }

        @Override
        public void appendDependencies(Object instance, Set<DefDescriptor<?>> deps) {
        }
    }

    /**
     * A fake registry to check locking as we call.
     */
    @SuppressWarnings("serial")
    private static class FakeRegistry implements DefRegistry<TypeDef> {
        public DefDescriptor<TypeDef> desc;
        public TypeDef def;
        private final Lock rLock;

        public FakeRegistry(Lock rLock, Lock wLock) {
            this.desc = Aura.getDefinitionService().getDefDescriptor("java://fake.type", TypeDef.class);
            this.def = new FakeTypeDef(desc);
            this.rLock = rLock;
        }

        @Override
        public TypeDef getDef(DefDescriptor<TypeDef> descriptor) throws QuickFixException {
            Mockito.verify(rLock, Mockito.times(1)).lock();
            Mockito.verify(rLock, Mockito.never()).unlock();
            if (descriptor.equals(desc)) {
                return def;
            }
            return null;
        }

        @Override
        public boolean hasFind() {
            return true;
        }

        @Override
        public Set<DefDescriptor<?>> find(DescriptorFilter matcher) {
            Mockito.verify(rLock, Mockito.times(1)).lock();
            Mockito.verify(rLock, Mockito.never()).unlock();
            Set<DefDescriptor<?>> found = Sets.newHashSet();
            found.add(desc);
            return found;
        }

        @Override
        public boolean exists(DefDescriptor<TypeDef> descriptor) {
            Mockito.verify(rLock, Mockito.times(1)).lock();
            Mockito.verify(rLock, Mockito.never()).unlock();
            return desc.equals(descriptor);
        }

        @Override
        public Set<DefType> getDefTypes() {
            Set<DefType> types = Sets.newHashSet();
            types.add(DefType.TYPE);
            return types;
        }

        @Override
        public Set<String> getPrefixes() {
            Set<String> prefixes = Sets.newHashSet();
            prefixes.add("java");
            return prefixes;
        }

        @Override
        public Set<String> getNamespaces() {
            Set<String> prefixes = Sets.newHashSet();
            prefixes.add("fake");
            return prefixes;
        }

        @Override
        public Source<TypeDef> getSource(DefDescriptor<TypeDef> descriptor) {
            return null;
        }

        @Override
        public boolean isCacheable() {
            return true;
        }

        @Override
        public boolean isStatic() {
            return false;
        }
    }

    /**
     * A private class to hold all the info for a lock test.
     *
     * This sets up the mocks so that we can test locking, if it is instantiated, you _must_ call clear() in a finally
     * block. The locking is not real here, so have a care.
     */
//    private static class LockTestInfo {
//        public final MasterDefRegistryImpl mdr;
//        public final FakeRegistry reg;
//        public final Lock rLock;
//        public final Lock wLock;
//
//        public LockTestInfo() {
//            ServiceLoader sl = ServiceLocatorMocker.spyOnServiceLocator();
//            this.rLock = Mockito.mock(Lock.class, "rLock");
//            this.wLock = Mockito.mock(Lock.class, "wLock");
//            CachingService acs = Mockito.spy(sl.get(CachingService.class));
//            Mockito.stub(sl.get(CachingService.class)).toReturn(acs);
//            Mockito.stub(acs.getReadLock()).toReturn(rLock);
//            Mockito.stub(acs.getWriteLock()).toReturn(wLock);
//            this.reg = new FakeRegistry(rLock, wLock);
//            this.mdr = new MasterDefRegistryImpl(reg);
//        }
//
//        public void clear() {
//            ServiceLocatorMocker.unmockServiceLocator();
//        }
//    }

    /**
     * Test getDef to ensure locking is minimized.
     *
     * This asserts that within an MDR we only lock once for any number of getDef calls for a single def.
     *
     * FIXME: this should not hit the caching service.
     */
//    public void testGetDefLocking() throws Exception {
//        LockTestInfo lti = null;
//
//        lti = new LockTestInfo();
//        try {
//            Definition d = lti.mdr.getDef(lti.reg.desc);
//            Mockito.verify(lti.rLock, Mockito.times(1)).lock();
//            Mockito.verify(lti.rLock, Mockito.times(1)).unlock();
//            assertEquals(d, lti.mdr.getDef(lti.reg.desc));
//            Mockito.verify(lti.rLock, Mockito.times(1)).lock();
//            Mockito.verify(lti.rLock, Mockito.times(1)).unlock();
//            Mockito.verify(lti.wLock, Mockito.never()).lock();
//            Mockito.verify(lti.wLock, Mockito.never()).unlock();
//        } finally {
//            lti.clear();
//        }
//    }

    /**
     * Test find(matcher) to ensure locking is minimized.
     *
     * This asserts that within an MDR we only lock once for a call to find.
     */
//    public void testFindMatcherLocking() throws Exception {
//        LockTestInfo lti = null;
//
//        lti = new LockTestInfo();
//        try {
//            lti.mdr.find(new DescriptorFilter("bah:hum*"));
//            Mockito.verify(lti.rLock, Mockito.times(1)).lock();
//            Mockito.verify(lti.rLock, Mockito.times(1)).unlock();
//            lti.mdr.find(new DescriptorFilter("bah:hum*"));
//            // we always lock, so we cannot check for a single lock here.
//            Mockito.verify(lti.rLock, Mockito.times(2)).lock();
//            Mockito.verify(lti.rLock, Mockito.times(2)).unlock();
//            Mockito.verify(lti.wLock, Mockito.never()).lock();
//            Mockito.verify(lti.wLock, Mockito.never()).unlock();
//        } finally {
//            lti.clear();
//        }
//    }

    /**
     * Test exists to ensure locking is minimized.
     *
     * This asserts that within an MDR we only lock once for any number of calls to exists.
     */
//    public void testExistsLocking() throws Exception {
//        LockTestInfo lti = null;
//
//        lti = new LockTestInfo();
//        try {
//            lti.mdr.exists(lti.reg.desc);
//            Mockito.verify(lti.rLock, Mockito.times(1)).lock();
//            Mockito.verify(lti.rLock, Mockito.times(1)).unlock();
//            lti.mdr.exists(lti.reg.desc);
//            Mockito.verify(lti.rLock, Mockito.times(1)).lock();
//            Mockito.verify(lti.rLock, Mockito.times(1)).unlock();
//            Mockito.verify(lti.wLock, Mockito.never()).lock();
//            Mockito.verify(lti.wLock, Mockito.never()).unlock();
//        } finally {
//            lti.clear();
//        }
//    }

    /**
     * Test getUid for locking.
     *
     * getUid always takes the lock, maybe we should avoid this?
     */
//    public void testGetUidLocking() throws Exception {
//        LockTestInfo lti = null;
//
//        lti = new LockTestInfo();
//        try {
//            lti.mdr.getUid(null, lti.reg.desc);
//            Mockito.verify(lti.rLock, Mockito.times(1)).lock();
//            Mockito.verify(lti.rLock, Mockito.times(1)).unlock();
//            lti.mdr.getUid(null, lti.reg.desc);
//            // We lock every time here. Probably should not.
//            Mockito.verify(lti.rLock, Mockito.times(2)).lock();
//            Mockito.verify(lti.rLock, Mockito.times(2)).unlock();
//            Mockito.verify(lti.wLock, Mockito.never()).lock();
//            Mockito.verify(lti.wLock, Mockito.never()).unlock();
//        } finally {
//            lti.clear();
//        }
//    }

    private class MasterDefRegistryImplOverride extends MasterDefRegistryImpl {
        public MasterDefRegistryImplOverride(@Nonnull DefRegistry<?>... registries) {
            super(registries);
        }

        @Override
        public <T extends Definition> DependencyEntry compileDE(@Nonnull DefDescriptor<T> descriptor)
                throws QuickFixException {
            return super.compileDE(descriptor);
        }
    }
}

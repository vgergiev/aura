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
package org.auraframework.impl.root.component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.auraframework.Aura;
import org.auraframework.def.AttributeDef;
import org.auraframework.def.AttributeDefRef;
import org.auraframework.def.BaseComponentDef;
import org.auraframework.def.BaseComponentDef.WhitespaceBehavior;
import org.auraframework.def.ComponentDef;
import org.auraframework.def.ControllerDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.DefDescriptor.DefType;
import org.auraframework.def.DefinitionAccess;
import org.auraframework.def.DependencyDef;
import org.auraframework.def.EventHandlerDef;
import org.auraframework.def.HelperDef;
import org.auraframework.def.ImportDef;
import org.auraframework.def.InterfaceDef;
import org.auraframework.def.ModelDef;
import org.auraframework.def.RegisterEventDef;
import org.auraframework.def.RendererDef;
import org.auraframework.def.RootDefinition.SupportLevel;
import org.auraframework.def.StyleDef;
import org.auraframework.expression.PropertyReference;
import org.auraframework.impl.expression.PropertyReferenceImpl;
import org.auraframework.impl.root.RootDefinitionImplUnitTest;
import org.auraframework.impl.root.component.BaseComponentDefImpl.Builder;
import org.auraframework.impl.system.DefDescriptorImpl;
import org.auraframework.system.AuraContext.Authentication;
import org.auraframework.system.AuraContext.Format;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.system.Location;
import org.auraframework.throwable.AuraRuntimeException;
import org.auraframework.throwable.NoAccessException;
import org.auraframework.throwable.quickfix.InvalidAccessValueException;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

public abstract class BaseComponentDefImplUnitTest<I extends BaseComponentDefImpl<D>, D extends BaseComponentDef, B extends Builder<D>>
extends RootDefinitionImplUnitTest<I, D, B> {

    protected boolean isAbstract;
    protected boolean isExtensible;
    protected boolean isTemplate;
    @Mock
    protected DefDescriptor<ModelDef> modelDefDescriptor;
    @Mock
    protected DefDescriptor<D> extendsDescriptor;
    @Mock
    protected DefDescriptor<ComponentDef> templateDefDescriptor;
    @Mock
    protected ComponentDef templateDef;
    @Mock
    protected DefDescriptor<StyleDef> styleDescriptor;
    protected List<DefDescriptor<RendererDef>> rendererDescriptors;
    protected List<DefDescriptor<HelperDef>> helperDescriptors;
    protected Set<DefDescriptor<InterfaceDef>> interfaces;
    protected List<DefDescriptor<ControllerDef>> controllerDescriptors;
    protected Map<String, RegisterEventDef> events;
    protected List<EventHandlerDef> eventHandlers;
    protected List<ImportDef> imports;
    protected List<AttributeDefRef> facets;
    protected Set<PropertyReference> expressionRefs;
    protected String render;
    protected WhitespaceBehavior whitespaceBehavior;
    protected List<DependencyDef> dependencies;
    @Mock
    protected DefDescriptor<ControllerDef> mockControllerDef;

    protected static DefinitionAccess GLOBAL_ACCESS;
    protected static DefinitionAccess PRIVATE_ACCESS;

    static {
        try {
            GLOBAL_ACCESS = Aura.getDefinitionParserAdapter().parseAccess(null, "GLOBAL");
        } catch (InvalidAccessValueException x) {
            throw new AuraRuntimeException(x);
        }
        try {
            GLOBAL_ACCESS = Aura.getDefinitionParserAdapter().parseAccess(null, "PRIVATE");
        } catch (InvalidAccessValueException x) {
            throw new AuraRuntimeException(x);
        }
    }


    public BaseComponentDefImplUnitTest(String name) {
        super(name);
    }


    public void testAppendDependenciesDefaultValue() throws Exception {
        Set<DefDescriptor<?>> dependencies = Mockito.spy(Sets.<DefDescriptor<?>> newHashSet());
        buildDefinition().appendDependencies(dependencies);
    }

    @Override
    public void testValidateReferences() throws Exception {
        setupValidateReferences();
        this.extendsDescriptor = null;
        this.modelDefDescriptor = null;
        setupTemplate(true);
        buildDefinition().validateReferences();
    }
    
    //test for W-2798390
    @SuppressWarnings("unchecked")
	@Override
    public void testValidateDefinition() throws Exception {
        //set up controllerDescriptors here to make sure we don't check it when validating definition
        this.controllerDescriptors = new ArrayList<DefDescriptor<ControllerDef>>();
        this.mockControllerDef = Mockito.mock(DefDescriptor.class);
        this.controllerDescriptors.add(mockControllerDef);
        this.modelDefDescriptor = Mockito.mock(DefDescriptor.class);
        testAuraContext = Aura.getContextService().startContext(Mode.UTEST, Format.JSON, Authentication.AUTHENTICATED);
        
        setupTemplate(true);
        buildDefinition().validateDefinition();
        
        //verify we didn't touch controllerDef/modelDef, that's validateReference's job, not validateDefinition
        Mockito.verify(this.mockControllerDef, Mockito.times(0)).getDef();
        Mockito.verify(this.modelDefDescriptor, Mockito.times(0)).getDef();
    }
    
    public void testValidateReferencesExpressionToOwnPrivateAttribute() throws Exception {
        setupValidateReferences();

        DefDescriptor<AttributeDef> attrDesc = DefDescriptorImpl.getInstance("privateAttribute", AttributeDef.class);
        AttributeDef attrDef = Mockito.mock(AttributeDef.class);
        Mockito.doReturn(attrDesc).when(attrDef).getDescriptor();
        Mockito.doReturn(PRIVATE_ACCESS).when(attrDef).getAccess();

        @SuppressWarnings("unchecked")
        D parentDef = (D) Mockito.mock(getBuilder().getClass().getDeclaringClass());
        Mockito.doReturn(this.extendsDescriptor).when(parentDef).getDescriptor();
        Mockito.doReturn(ImmutableMap.of()).when(parentDef).getAttributeDefs();
        Mockito.doReturn(true).when(parentDef).isExtensible();
        Mockito.doReturn(SupportLevel.GA).when(parentDef).getSupport();
        Mockito.doReturn(GLOBAL_ACCESS).when(parentDef).getAccess();
        Mockito.doReturn(parentDef).when(this.extendsDescriptor).getDef();
        Mockito.doReturn(DefType.COMPONENT).when(this.extendsDescriptor).getDefType();

        this.expressionRefs = Sets.newHashSet();
        this.expressionRefs.add(new PropertyReferenceImpl("v.privateAttribute", null));
        this.attributeDefs = ImmutableMap.of(attrDesc, attrDef);
        setupTemplate(true);

        //FIXME:
        //buildDefinition().validateReferences();
    }

    public void testValidateReferencesExpressionToSuperPrivateAttribute() throws Exception {
        setupValidateReferences();

        DefDescriptor<AttributeDef> attrDesc = DefDescriptorImpl.getInstance("privateAttribute", AttributeDef.class);
        AttributeDef attrDef = Mockito.mock(AttributeDef.class);
        Mockito.doReturn(attrDesc).when(attrDef).getDescriptor();
        Mockito.doReturn(PRIVATE_ACCESS).when(attrDef).getAccess();

        @SuppressWarnings("unchecked")
        D parentDef = (D) Mockito.mock(getBuilder().getClass().getDeclaringClass());
        Mockito.doReturn(this.extendsDescriptor).when(parentDef).getDescriptor();
        Mockito.doReturn(ImmutableMap.of(attrDesc, attrDef)).when(parentDef).getAttributeDefs();
        Mockito.doReturn(true).when(parentDef).isExtensible();
        Mockito.doReturn(SupportLevel.GA).when(parentDef).getSupport();
        Mockito.doReturn(parentDef).when(this.extendsDescriptor).getDef();
        Mockito.doReturn(GLOBAL_ACCESS).when(parentDef).getAccess();
        Mockito.doReturn(DefType.COMPONENT).when(this.extendsDescriptor).getDefType();

        Location exprLocation = new Location("expression", 0);
        this.expressionRefs = Sets.newHashSet();
        this.expressionRefs.add(new PropertyReferenceImpl("v.privateAttribute", exprLocation));
        this.attributeDefs = ImmutableMap.of();
        setupTemplate(true);
        modelDefDescriptor = null;

        try {
            buildDefinition().validateReferences();
            fail("Expected an exception when trying to refer to a private attribute in an expression");
        } catch (Throwable t) {
            assertExceptionMessageStartsWith(t, NoAccessException.class,
                    "Access to COMPONENT");
            //FIXME: we should have a better location here.
            //assertEquals(exprLocation, ((NoAccessException) t).getLocation());
        }
    }

    public void testTemplateMustBeTemplate() throws Exception {
        setupValidateReferences();
        this.extendsDescriptor = null;
        this.modelDefDescriptor = null;
        setupTemplate(false);
        try {
            buildDefinition().validateReferences();
            fail("Expected an exception when using a template that is not a template");
        } catch (Throwable t) {
            assertExceptionMessageStartsWith(t, InvalidDefinitionException.class,
                    String.format("Template %s must be marked as a template", templateDefDescriptor));
        }
    }

    public void testValidateReferencesExpressionToOwnPrivateAttributeOverridingSuper() throws Exception {
        setupValidateReferences();

        DefDescriptor<AttributeDef> attrDesc = DefDescriptorImpl.getInstance("privateAttribute", AttributeDef.class);
        AttributeDef attrDef = Mockito.mock(AttributeDef.class);
        Mockito.doReturn(attrDesc).when(attrDef).getDescriptor();
        Mockito.doReturn(PRIVATE_ACCESS).when(attrDef).getAccess();

        @SuppressWarnings("unchecked")
        D parentDef = (D) Mockito.mock(getBuilder().getClass().getDeclaringClass());
        Mockito.doReturn(this.extendsDescriptor).when(parentDef).getDescriptor();
        Mockito.doReturn(ImmutableMap.of(attrDesc, attrDef)).when(parentDef).getAttributeDefs();
        Mockito.doReturn(true).when(parentDef).isExtensible();
        Mockito.doReturn(SupportLevel.GA).when(parentDef).getSupport();
        Mockito.doReturn(parentDef).when(this.extendsDescriptor).getDef();
        Mockito.doReturn(GLOBAL_ACCESS).when(parentDef).getAccess();
        Mockito.doReturn(DefType.COMPONENT).when(this.extendsDescriptor).getDefType();

        this.expressionRefs = Sets.newHashSet();
        this.expressionRefs.add(new PropertyReferenceImpl("v.privateAttribute", null));
        this.attributeDefs = ImmutableMap.of(attrDesc, attrDef);
        this.modelDefDescriptor = null;

        setupTemplate(true);

        try {
            buildDefinition().validateReferences();
        } catch (NoAccessException expected) {
            return;
        }
        fail("Should have failed with a no access exception");
    }

    @Override
    protected void setupValidateReferences() throws Exception {
        this.interfaces = Sets.newHashSet();
        this.interfaces.add(BaseComponentDefImpl.ROOT_MARKER);
        testAuraContext = Aura.getContextService().startContext(Mode.UTEST, Format.JSON, Authentication.AUTHENTICATED);
    }

    @Override
    protected D buildDefinition(B builder) throws Exception {
        builder.setAbstract(this.isAbstract);
        builder.setExtensible(this.isExtensible);
        builder.isTemplate = this.isTemplate;
        builder.modelDefDescriptor = this.modelDefDescriptor;
        builder.extendsDescriptor = this.extendsDescriptor;
        builder.templateDefDescriptor = this.templateDefDescriptor;
        builder.styleDescriptor = this.styleDescriptor;
        builder.rendererDescriptors = this.rendererDescriptors;
        builder.helperDescriptors = this.helperDescriptors;
        builder.interfaces = this.interfaces;
        builder.controllerDescriptors = this.controllerDescriptors;
        builder.events = this.events;
        builder.eventHandlers = this.eventHandlers;
        builder.imports = this.imports;
        builder.facets = this.facets;
        builder.expressionRefs = this.expressionRefs;
        builder.render = this.render;
        builder.setWhitespaceBehavior(this.whitespaceBehavior);
        builder.dependencies = this.dependencies;
        return super.buildDefinition(builder);
    }

    protected void setupTemplate(boolean isTemplate) throws QuickFixException {
        Mockito.doReturn(this.templateDef).when(this.templateDefDescriptor).getDef();
        Mockito.doReturn(this.templateDefDescriptor).when(this.templateDef).getDescriptor();
        Mockito.doReturn(isTemplate).when(this.templateDef).isTemplate();
    }
}

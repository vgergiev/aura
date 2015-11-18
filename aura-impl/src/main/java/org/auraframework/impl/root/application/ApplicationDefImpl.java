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
package org.auraframework.impl.root.application;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.auraframework.Aura;
import org.auraframework.builder.ApplicationDefBuilder;
import org.auraframework.def.ActionDef;
import org.auraframework.def.ApplicationDef;
import org.auraframework.def.ControllerDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.EventDef;
import org.auraframework.expression.Expression;
import org.auraframework.expression.PropertyReference;
import org.auraframework.impl.AuraImpl;
import org.auraframework.impl.root.component.BaseComponentDefImpl;
import org.auraframework.impl.system.DefDescriptorImpl;
import org.auraframework.impl.util.TextTokenizer;
import org.auraframework.instance.Action;
import org.auraframework.system.AuraContext;
import org.auraframework.throwable.AuraRuntimeException;
import org.auraframework.throwable.quickfix.InvalidDefinitionException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.json.Json;

/**
 * The definition of an Application. Holds all information about a given type of application. ApplicationDefs are
 * immutable singletons per type of Application. Once they are created, they can only be replaced, never changed.
 */
public class ApplicationDefImpl extends BaseComponentDefImpl<ApplicationDef> implements ApplicationDef {

    public static final DefDescriptor<ApplicationDef> PROTOTYPE_APPLICATION = DefDescriptorImpl.getInstance(
            "markup://aura:application", ApplicationDef.class);

    protected ApplicationDefImpl(Builder builder) {
        super(builder);

        this.locationChangeEventDescriptor = builder.locationChangeEventDescriptor;

        this.isAppcacheEnabled = builder.isAppcacheEnabled;
        this.additionalAppCacheURLs = builder.additionalAppCacheURLs;
        this.isOnePageApp = builder.isOnePageApp;
    }

    public static class Builder extends BaseComponentDefImpl.Builder<ApplicationDef>implements ApplicationDefBuilder {
        public DefDescriptor<EventDef> locationChangeEventDescriptor;
        public Boolean isAppcacheEnabled;
        public Boolean isOnePageApp;
        public String additionalAppCacheURLs;

        public Builder() {
            super(ApplicationDef.class);
        }

        @Override
        public ApplicationDefImpl build() {
            finish();
            return new ApplicationDefImpl(this);
        }
    }

    @Override
    public DefDescriptor<ApplicationDef> getDefaultExtendsDescriptor() {
        return ApplicationDefImpl.PROTOTYPE_APPLICATION;
    }

    /**
     * @return Returns the locationChangeEventDescriptor.
     * @throws QuickFixException
     */
    @Override
    public DefDescriptor<EventDef> getLocationChangeEventDescriptor() throws QuickFixException {
        if (locationChangeEventDescriptor == null) {
            ApplicationDef superDef = getSuperDef();
            if (superDef != null) {
                return superDef.getLocationChangeEventDescriptor();
            }
            return null;
        } else {
            return locationChangeEventDescriptor;
        }
    }

    @Override
    protected void serializeFields(Json json) throws IOException, QuickFixException {
        DefDescriptor<EventDef> locationChangeEventDescriptor = getLocationChangeEventDescriptor();
        if (locationChangeEventDescriptor != null) {
            json.writeMapEntry("locationChangeEventDef", locationChangeEventDescriptor.getDef());
        }
    }

    @Override
    public void appendDependencies(Set<DefDescriptor<?>> dependencies) {
        super.appendDependencies(dependencies);

        if (locationChangeEventDescriptor != null) {
            dependencies.add(locationChangeEventDescriptor);
        }
    }

    @Override
    public Boolean isAppcacheEnabled() throws QuickFixException {
        if (isAppcacheEnabled != null) {
            return isAppcacheEnabled;
        }
        if (getExtendsDescriptor() != null) {
            AuraContext context = Aura.getContextService().getCurrentContext();
            if (context != null) {
                return context.getDefRegistry().getRawDef(getExtendsDescriptor()).isAppcacheEnabled();
            }
        }
        return Boolean.FALSE;
    }

    @Override
    public List<String> getAdditionalAppCacheURLs() throws QuickFixException {
        List<String> urls = Collections.emptyList();

        if (additionalAppCacheURLs != null) {
            Expression expression = AuraImpl.getExpressionAdapter().buildExpression(
                    TextTokenizer.unwrap(additionalAppCacheURLs), null);
            if (!(expression instanceof PropertyReference)) {
                throw new AuraRuntimeException(
                        "Value of 'additionalAppCacheURLs' attribute must be a reference to a server Action");
            }

            PropertyReference ref = (PropertyReference) expression;
            ref = ref.getStem();

            ControllerDef controllerDef = getLocalControllerDef();
            ActionDef actionDef = controllerDef.getSubDefinition(ref.toString());
            Action action = Aura.getInstanceService().getInstance(actionDef);

            AuraContext context = Aura.getContextService().getCurrentContext();
            Action previous = context.setCurrentAction(action);
            try {
                action.run();
            } finally {
                context.setCurrentAction(previous);
            }

            @SuppressWarnings("unchecked")
            List<String> additionalURLs = (List<String>) action.getReturnValue();
            if (additionalURLs != null) {
                urls = additionalURLs;
            }
        }

        return urls;
    }

    @Override
    public Boolean isOnePageApp() throws QuickFixException {
        return isOnePageApp;
    }

    @Override
    public void validateReferences() throws QuickFixException {
        super.validateReferences();

        // MasterDefRegistry reg =
        // Aura.getContextService().getCurrentContext().getDefRegistry();
        EventDef locationChangeDef = getLocationChangeEventDescriptor().getDef();
        if (!locationChangeDef.isInstanceOf(Aura.getDefinitionService().getDefDescriptor("aura:locationChange",
                EventDef.class))) {
            throw new InvalidDefinitionException(String.format("%s must extend aura:locationChange",
                    locationChangeDef.getDescriptor()), getLocation());
        }
    }

    private final DefDescriptor<EventDef> locationChangeEventDescriptor;

    private final Boolean isAppcacheEnabled;
    private final String additionalAppCacheURLs;

    private final Boolean isOnePageApp;

    private static final long serialVersionUID = 9044177107921912717L;
}

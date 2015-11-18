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
package org.auraframework.impl.adapter.format.html;

import java.io.IOException;
import java.util.Map;

import javax.annotation.concurrent.ThreadSafe;

import org.auraframework.Aura;
import org.auraframework.def.BaseComponentDef;
import org.auraframework.def.ComponentDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.StyleDef;
import org.auraframework.ds.serviceloader.AuraServiceProvider;
import org.auraframework.http.AuraBaseServlet;
import org.auraframework.instance.BaseComponent;
import org.auraframework.instance.Component;
import org.auraframework.service.InstanceService;
import org.auraframework.service.RenderingService;
import org.auraframework.system.AuraContext;
import org.auraframework.system.AuraContext.Mode;
import org.auraframework.throwable.AuraRuntimeException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.javascript.Literal;
import org.auraframework.util.json.JsonEncoder;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 */
@ThreadSafe
@aQute.bnd.annotation.component.Component (provide=AuraServiceProvider.class)
public abstract class BaseComponentHTMLFormatAdapter<T extends BaseComponent<?, ?>> extends HTMLFormatAdapter<T> {

    @Override
    public void write(T value, Map<String, Object> componentAttributes, Appendable out) throws IOException {
        try {

            AuraContext context = Aura.getContextService().getCurrentContext();
            InstanceService instanceService = Aura.getInstanceService();
            RenderingService renderingService = Aura.getRenderingService();
            BaseComponentDef def = value.getDescriptor().getDef();

            ComponentDef templateDef = def.getTemplateDef();
            Map<String, Object> attributes = Maps.newHashMap();

            StringBuilder sb = new StringBuilder();
            writeHtmlStyle(Aura.getConfigAdapter().getResetCssURL(), sb);
            attributes.put("auraResetTags", sb.toString());

            sb.setLength(0);
            writeHtmlStyles(Aura.getServletUtilAdapter().getStyles(context), sb);
            attributes.put("auraStyleTags", sb.toString());
            sb.setLength(0);
            writeHtmlScripts(Aura.getServletUtilAdapter().getScripts(context), sb);
            DefDescriptor<StyleDef> styleDefDesc = templateDef.getStyleDescriptor();
            if (styleDefDesc != null) {
                attributes.put("auraInlineStyle", styleDefDesc.getDef().getCode());
            }

            String contextPath = context.getContextPath();
            Mode mode = context.getMode();

            if (mode.allowLocalRendering() && def.isLocallyRenderable()) {
                BaseComponent<?,?> cmp = (BaseComponent<?,?>)instanceService.getInstance(def, componentAttributes);

                attributes.put("body", Lists.<BaseComponent<?, ?>> newArrayList(cmp));
                attributes.put("bodyClass", "");
                attributes.put("autoInitialize", "false");

                Component template = instanceService.getInstance(templateDef.getDescriptor(), attributes);

                renderingService.render(template, out);
            } else {

                attributes.put("auraScriptTags", sb.toString());
                Map<String, Object> auraInit = Maps.newHashMap();
                if (componentAttributes != null && !componentAttributes.isEmpty()) {
                    auraInit.put("attributes", componentAttributes);
                }
                auraInit.put("descriptor", def.getDescriptor());
                auraInit.put("deftype", def.getDescriptor().getDefType());
                auraInit.put("host", contextPath);

                attributes.put("autoInitialize", "false");
                attributes.put("autoInitializeSync", "true");

                auraInit.put("instance", value);
                auraInit.put("token", AuraBaseServlet.getToken());

                StringBuilder contextWriter = new StringBuilder();
                Aura.getSerializationService().write(context, null, AuraContext.class, contextWriter, "JSON");
                auraInit.put("context", new Literal(contextWriter.toString()));

                attributes.put("auraInitSync", JsonEncoder.serialize(auraInit));

                Component template = instanceService.getInstance(templateDef.getDescriptor(), attributes);
                renderingService.render(template, out);
            }
        } catch (QuickFixException e) {
            throw new AuraRuntimeException(e);
        }
    }

}

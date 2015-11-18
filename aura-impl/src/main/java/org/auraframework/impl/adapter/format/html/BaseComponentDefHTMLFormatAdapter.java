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
import org.auraframework.http.ManifestUtil;
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
public abstract class BaseComponentDefHTMLFormatAdapter<T extends BaseComponentDef> extends HTMLFormatAdapter<T> {
    private ManifestUtil manifestUtil = new ManifestUtil();

    @Override
    public void write(T value, Map<String, Object> componentAttributes, Appendable out) throws IOException {
        try {
            InstanceService instanceService = Aura.getInstanceService();
            RenderingService renderingService = Aura.getRenderingService();

            ComponentDef templateDef = value.getTemplateDef();
            Map<String, Object> attributes = Maps.newHashMap();

            StringBuilder sb = new StringBuilder();
            writeHtmlStyle(Aura.getConfigAdapter().getResetCssURL(), sb);
            attributes.put("auraResetTags", sb.toString());

            AuraContext context = Aura.getContextService().getCurrentContext();

            sb.setLength(0);
            writeHtmlStyles(Aura.getServletUtilAdapter().getStyles(context), sb);
            attributes.put("auraStyleTags", sb.toString());

            DefDescriptor<StyleDef> styleDefDesc = templateDef.getStyleDescriptor();
            if (styleDefDesc != null) {
                attributes.put("auraInlineStyle", styleDefDesc.getDef().getCode());
            }

            String contextPath = context.getContextPath();
            Mode mode = context.getMode();

            if (mode.allowLocalRendering() && value.isLocallyRenderable()) {
                BaseComponent<?, ?> cmp = null;

                cmp = (BaseComponent<?, ?>) instanceService.getInstance(value, componentAttributes);

                attributes.put("body", Lists.<BaseComponent<?, ?>> newArrayList(cmp));
                attributes.put("bodyClass", "");
                attributes.put("defaultBodyClass", "");
                attributes.put("autoInitialize", "false");
            } else {
                if (manifestUtil.isManifestEnabled()) {
                    attributes.put("manifest", manifestUtil.getManifestUrl());
                }

                sb.setLength(0);
                writeHtmlScripts(Aura.getServletUtilAdapter().getBaseScripts(context), sb);
                attributes.put("auraBaseScriptTags", sb.toString());

                sb.setLength(0);
                writeHtmlScripts(Aura.getServletUtilAdapter().getNamespacesScripts(context), true, sb);
                attributes.put("auraNamespacesScriptTags", sb.toString());

                if(mode != Mode.PROD && mode != Mode.PRODDEBUG &&
                        Aura.getContextService().getCurrentContext().getIsDebugToolEnabled()) {
                    attributes.put("auraInitBlock", "<script>var debugWindow=window.open('/aura/debug.cmp','Aura Debug Tool','width=900,height=305,scrollbars=0,location=0,toolbar=0,menubar=0');$A.util.setDebugToolWindow(debugWindow);</script>");
                }

                Map<String, Object> auraInit = Maps.newHashMap();
                if (componentAttributes != null && !componentAttributes.isEmpty()) {
                    auraInit.put("attributes", componentAttributes);
                }

                auraInit.put("descriptor", value.getDescriptor());
                auraInit.put("deftype", value.getDescriptor().getDefType());
                auraInit.put("host", contextPath);

                auraInit.put("context", new Literal(context.serialize(AuraContext.EncodingStyle.Full)));

                attributes.put("auraInit", JsonEncoder.serialize(auraInit));
            }
            Component template = instanceService.getInstance(templateDef.getDescriptor(), attributes);
            renderingService.render(template, out);
        } catch (QuickFixException e) {
            throw new AuraRuntimeException(e);
        }
    }

    /**
     * @param manifestUtil the manifestUtil to set
     */
    public void setManifestUtil(ManifestUtil manifestUtil) {
        this.manifestUtil = manifestUtil;
    }

}

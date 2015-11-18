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
package org.auraframework.impl.css.parser;

import org.auraframework.adapter.StyleAdapter;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.StyleDef;
import org.auraframework.impl.adapter.StyleAdapterImplTest.TestStyleAdapter;
import org.auraframework.impl.adapter.format.css.StyleDefCSSFormatAdapter;
import org.auraframework.impl.css.StyleTestCase;
import org.auraframework.impl.css.parser.plugin.DuplicateFontFacePlugin;
import org.auraframework.throwable.AuraRuntimeException;

import com.google.common.collect.Lists;

public class TestDuplicateFontFacePlugin extends StyleTestCase {
    private DuplicateFontFacePlugin fontFamilyPlugin;
    private StringBuilder out;

    public TestDuplicateFontFacePlugin(String name) {
        super(name);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        out = new StringBuilder();

    }

    private StyleAdapter prepare(DuplicateFontFacePlugin plugin) {
        fontFamilyPlugin = plugin;
        TestStyleAdapter adapter = TestStyleAdapter.contextual(fontFamilyPlugin);
        return adapter;
    }

    public void testNoErrorOnDifferentFonts() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin());
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {font-family: Custom1; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {font-family: Custom2; src: url(Custom2.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
        // no error
    }

    public void testErrorsOnDupeFontsSameFile() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin());
        String s = "@font-face {font-family: Custom1; src: url(Custom1.woff)}";
        DefDescriptor<StyleDef> desc = addStyleDef(s + "\n" + s);

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }

    public void testErrorsOnDupeFontsDifferentFiles() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin());
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {font-family: Custom1; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {font-family: Custom2; src: url(Custom2.woff)}");
        DefDescriptor<StyleDef> desc3 = addStyleDef("@font-face {font-family: Custom1; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef(), desc3.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }

    public void testErrorsOnDupeQuotedAndUnquoted() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin());
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {font-family: Custom1; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {font-family: \"Custom1\"; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }

    public void testErrorsOnDupeDifferentQuotes() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin());
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {font-family: 'Custom1'; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {font-family: \"Custom1\"; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }

    public void testSamePropsCheckAllOn() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin(false, true));
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }

    public void testSamePropsCheckAllOff() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin(false, false));
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }

    public void testDifferentPropsCheckAllOn() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin(false, true));
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {font-family: Custom1; font-style:italic; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
        // no error
    }

    public void testDifferentPropsCheckAllOff() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin(false, false));
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {font-family: Custom1; font-style:italic; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }

    public void testSamePropsBothHaveAnnotation() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin(true, false));
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {/* @allowDuplicate */ font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {/* @allowDuplicate */ font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
        // no error
    }

    public void testSamePropsOnlyFirstHasAnnotation() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin(true, false));
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {/* @allowDuplicate */ font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }

    public void testSamePropsOnlySecondHasAnnotation() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin(true, false));
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {/* @allowDuplicate */ font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }

    public void testSamePropsBothHaveAnnotationButNotAllowed() throws Exception {
        StyleAdapter adapter = prepare(new DuplicateFontFacePlugin(false, false));
        DefDescriptor<StyleDef> desc1 = addStyleDef("@font-face {/* @allowDuplicate */ font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");
        DefDescriptor<StyleDef> desc2 = addStyleDef("@font-face {/* @allowDuplicate */ font-family: Custom1; font-weight:bold; src: url(Custom1.woff)}");

        StyleDefCSSFormatAdapter cssFormatAdapter = new StyleDefCSSFormatAdapter();
        cssFormatAdapter.setStyleAdapter(adapter);
        try {
            cssFormatAdapter.writeCollection(Lists.newArrayList(desc1.getDef(), desc2.getDef()), out);
            fail("expected to get exception");
        } catch (Exception e) {
            checkExceptionContains(e, AuraRuntimeException.class, "was already declared");
        }
    }
}

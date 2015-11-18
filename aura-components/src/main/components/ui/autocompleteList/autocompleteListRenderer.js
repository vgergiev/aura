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
({
    afterRender : function(component, helper){
        this.superAfterRender();
        helper.updateEmptyListContent(component);
        helper.setUpEvents(component);
        helper.addHeaderAndFooterClassesAndAttributes(component);
        helper.setDefaultHighlight(component);
    },
    rerender : function(component, helper){
        this.superRerender();
        if (component.isDirty('v.visible')) {
            helper.updateEmptyListContent(component);
        }
        helper.setDefaultHighlight(component);
    },
    unrender: function(component, helper) {
        if (helper.getOnClickEventProp.cache && 
            helper.getOnClickEventProp.cache.onClickStartEvent && 
            component._onClickStartFunc) {
            $A.util.removeOn(document.body, helper.getOnClickEventProp.cache.onClickStartEvent, component._onClickStartFunc, false);
        }
        if (helper.getOnClickEventProp.cache &&
            helper.getOnClickEventProp.cache.onClickEndEvent && 
            component._onClickEndFunc) {
            $A.util.removeOn(document.body, helper.getOnClickEventProp.cache.onClickEndEvent, component._onClickEndFunc, false);
        }
        this.superUnrender();
    }
})// eslint-disable-line semi

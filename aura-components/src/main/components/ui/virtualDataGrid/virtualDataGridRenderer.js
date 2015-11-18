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
    render: function (cmp, helper) {
        var dom       = this.superRender(),
            container = helper.getGridBody(cmp),
            items     = cmp._virtualItems,
            fragment  = document.createDocumentFragment();
        
        for (var i = 0; i < items.length; i++) {
            fragment.appendChild(items[i]);
        }

        // Create the events for delegation
        helper.createEventDelegates(cmp, container);
        container.appendChild(fragment);
        
        return dom;

    },
    afterRender: function (cmp, helper) {
    	if (cmp.get("v.fixedHeader")) {
    		helper.initializeFixedHeader(cmp);
    	}
    	
    	if (cmp.get("v.enableResizableColumns")) {
    		helper.enableColumnResizer(cmp);
    	}
    	
        this.superAfterRender();
    },
    rerender: function (cmp, helper) {
        this.superRerender();

        var container = helper.getGridBody(cmp),
            items     = cmp._virtualItems,
            fragment  = document.createDocumentFragment();
        
        for (var i = 0; i < items.length; i++) {
            fragment.appendChild(items[i]);
        }

        while (container.firstChild) {
        	container.removeChild(container.firstChild);
        }
        container.appendChild(fragment);
        
        if (cmp.get("v.fixedHeader")) {
    		helper.updateSizesForFixedHeader(cmp);
    	}
        
        if (cmp.get("v.enableResizableColumns") && !helper.hasResizerHandles(cmp)) {
        	helper.updateColumnResizer(cmp);
        	cmp._updateResizer = null;
        }
    },
    unrender: function (cmp, helper) {
        helper.destroyTemplates(cmp);
        this.superUnrender();
    }
})// eslint-disable-line semi
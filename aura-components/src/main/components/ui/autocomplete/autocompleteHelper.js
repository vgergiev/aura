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
	/**
	 * Map from the element aria attributes to the component attribute names
	 * since attributes with hyphens currently don't work inside expressions.
	 *
	 * TODO: This needs to live somewhere more accessible to most components
	 * that deal with aria attributes.
	 */
	ariaAttributeMap: {
		"aria-expanded": "ariaExpanded",
		"aria-activedescendant": "ariaActiveDescendant"
	},

    fetchData: function(component, event) {
        // Show loading indicator
        var listCmp = component.find("list");
        if (listCmp) {
            var listHelper = listCmp.getDef().getHelper();
            listHelper.showLoading(listCmp.getSuper(), true);
        }

        // set keyword to list component
        var options = event.getParam("parameters");
        if (listCmp) {
            listCmp.set("v.keyword", options.keyword);
        }
        
        // fire dataProvide event
        var dataProviders = component.get("v.dataProvider");
        var index = event.getParam("index");
        if (!index) {
            index = 0;
        }
        
        var provideEvent = dataProviders[index].get("e.provide");
        provideEvent.setParams({
            parameters: options
        });
        
        provideEvent.fire();
    },
    
    fireInputChangeEvent: function(component) {
    	// Hide the list if it is already visible
        this.hideList(component);
             
        //handling case when there is another element like label in the markup
        var value = component.getDef().getHelper().getInputElement(component).value;
        var inputChangeEvt = component.get("e.inputChange");
        var el = component.getDef().getHelper().getInputElement(component);

        // IE 11 fires the input event when we tab off, 
        // causing it to reopen. 
        // 
        // if this event is fired and the element is not focused, ignore
        if (inputChangeEvt && (el === document.activeElement)) {
            inputChangeEvt.setParams({
                value: value
            });
            inputChangeEvt.fire();
        }
    },
    
    hideList: function(component) {
        var list = component.find("list");
        if (list && list.get("v.visible") === true) {
            list.set("v.visible", false);
        }
    },

    handleEnterkey: function(component) {
        var list = component.find("list");
        if (list.get("v.visible") === true) {
            this.handleEnterkeyOnList(component, list);
        } else {
            this.handleEnterKeyOnInput(component, component.find("input"));
        }
    },

    handleEnterkeyOnList: function(component, list) {
        var optionSelectEvt = component.get("e.selectListOption");
        if (list.get("v.headerSelected")) {
            optionSelectEvt.setParams({ option:  component.get("v.listHeader"), isHeader: true  });
            optionSelectEvt.fire();
        } else if (list.get("v.footerSelected")) {
            optionSelectEvt.setParams({ option:  component.get("v.listFooter"), isFooter: true  });
            optionSelectEvt.fire();
        } else {
            var pEvent = list.get("e.pressOnHighlighted");
            pEvent.fire();
        }
    },

    handleEnterKeyOnInput: function() {
    },

    handleEsckey: function(component) {
    this.hideList(component);
    },
    
    handleKeyAction: function(component, event) {
        var keyCode = event.getParam("keyCode");
        var domEvent = event.getParam("domEvent");
        if (keyCode === 40) {  // down arrow key
            domEvent.preventDefault();
            this.highlightNextItem(component, event);
        } else if (keyCode === 38) {  // up arrow key
            domEvent.preventDefault();
            this.highlightPrevItem(component, event);
        } else if (keyCode === 27) {  // Esc key
            domEvent.stopPropagation();
            this.handleEsckey(component, event);
        } else if (keyCode === 9) {  // tab key: dismiss the list
            this.handleTabkey(component, event);
        } else if (keyCode === 13) {  // enter key: select the highlighted list option
            this.handleEnterkey(component, event);
        } else {
            this.handleOtherKeyAction(component, component.find("input"), event);
        }
    },

    handleOtherKeyAction: function() {
    },

    handleTabkey: function(component) {
        this.hideList(component);
    },
    
    highlightNextItem: function(component) {
        var list = component.find("list");
        if (list.get("v.visible") === true) {
            var highlightEvent = list.get("e.listHighlight");
            highlightEvent.setParams({
                activeIndex: 0
            });
            highlightEvent.fire();
        }
    },
    
    highlightPrevItem: function(component) {
        var list = component.find("list");
        if (list.get("v.visible") === true) {
            var highlightEvent = list.get("e.listHighlight");
            highlightEvent.setParams({
                activeIndex: -1
            });
            highlightEvent.fire();
        }
    },
    
    /**
     * Any event fired on input field, we need fire a same one on autocomplete.
     */
    relayEvents: function(component) {
    	var inputCmp = component.find("input");
        if (inputCmp) {
        	var handledEvents = component.getHandledEvents();
        	for ( var name in handledEvents) {
        		var eventDef = inputCmp.getDef().getEventDef(name);
    			if (eventDef && handledEvents.hasOwnProperty(name) && handledEvents[name] === true) {
                    inputCmp.addHandler(name, component, "c.fireEventsFromInput");
    			}
    		}
        } 
    },
    
    /**
     * Tell list component which elements it should ignore to handle collapse.
     *
     */
    setIgnoredElements: function(component) {
        var inputCmp = component.find("input");
        var listCmp = component.find("list");
        if (inputCmp && listCmp) {
            var elems = inputCmp.getElements();
            listCmp.set("v.ignoredElements", elems);
        }
    },

    addIgnoredElement: function(component, element) {
        var listCmp = component.find("list");
        if (listCmp) {
            var ignoreElements = listCmp.get("v.ignoredElements");
            ignoreElements.push(element);
        }
    },

    updateAriaAttributes: function(component, event) {
        var inputCmp = component.find("input");
        if (inputCmp) {
        	var attrs = event.getParam("attrs");
        	for (var key in attrs) {
        		if (attrs.hasOwnProperty(key) && this.ariaAttributeMap[key]) {
        			inputCmp.set("v." + this.ariaAttributeMap[key], attrs[key]);
        		}
        	}
        }
    },

    handleListExpand: function() {
    }
})// eslint-disable-line semi

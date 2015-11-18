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
     * Find out the current highlighted option.
     * @return the index of the highlighted component; -1 if no opton is highlighted now.
     */
    findHighlightedOptionIndex: function (iters) {
        for (var i = 0; i < iters.length; i++) {
            var optionCmp = iters[i];
            if (optionCmp.get("v.visible") === true && optionCmp.get("v.highlighted") === true) {
                return i;
            }
        }
        return -1;
    },

    /**
     * Notify that the matching is done.
     */
    fireMatchDoneEvent: function (component, items) {
        var size = 0;
        for (var i = 0; i < items.length; i++) {
            if (items[i].visible === true) {
                size++;
            }
        }
        var evt = component.get("e.matchDone");
        if (evt) {
            evt.setParams({
                size: size
            });
            evt.fire();
        }
    },

    getEventSourceOptionComponent: function (component, event) {
        //option could be a compound component so look for the right option
        var element = event.target || event.srcElement;
        var targetCmp;
        do {
            var htmlCmp = $A.componentService.getRenderingComponentForElement(element);
            if ($A.util.isUndefinedOrNull(htmlCmp)) {
                return null;
            }
            targetCmp = htmlCmp.getComponentValueProvider().getConcreteComponent();
            element = targetCmp.getElement().parentElement;
        } while (!targetCmp.isInstanceOf("ui:autocompleteOptionTemplate"));
        return targetCmp;
    },

    getOnClickEndFunction: function (component) {
        if ($A.util.isUndefined(component._onClickEndFunc)) {
            var helper = this;
            var i;
            var f = function (event) {
                // ignore gestures/swipes; only run the click handler if it's a click or tap
                var clickEndEvent;

                if (helper.getOnClickEventProp("isTouchDevice")) {
                    var touchIdFound = false;
                    for (i = 0; i < event.changedTouches.length; i++) {
                        clickEndEvent = event.changedTouches[i];
                        if (clickEndEvent.identifier === component._onStartId) {
                            touchIdFound = true;
                            break;
                        }
                    }

                    if (helper.getOnClickEventProp("isTouchDevice") && !touchIdFound) {
                        return;
                    }
                } else {
                    clickEndEvent = event;
                }

                var startX = component._onStartX, startY = component._onStartY;
                var endX = clickEndEvent.clientX, endY = clickEndEvent.clientY;

                if (Math.abs(endX - startX) > 0 || Math.abs(endY - startY) > 0) {
                    return;
                }

                var listElems = component.getElements();
                var ignoreElements = component.get("v.ignoredElements");
                var clickOutside = true;
                if (listElems) {
                    var ret = true;
                    for (i = 0; ret; i++) {
                        ret = listElems[i];
                        if (ret && helper.isHTMLElement(ret) && $A.util.contains(ret, event.target)) {
                            clickOutside = false;
                            break;
                        }
                    }
                }
                if (ignoreElements && clickOutside === true) {
                    var ret2 = true;
                    for (var j = 0; ret2; j++) {
                        ret2 = ignoreElements[j];
                        if (ret2 && helper.isHTMLElement(ret2) && $A.util.contains(ret2, event.target)) {
                            clickOutside = false;
                            break;
                        }
                    }
                }
                if (clickOutside === true) {
                    // Collapse the menu
                    component.set("v.visible", false);
                }
            };
            component._onClickEndFunc = f;
        }
        return component._onClickEndFunc;
    },

    getOnClickEventProp: function (prop) {
        // create the cache
        if ($A.util.isUndefined(this.getOnClickEventProp.cache)) {
            this.getOnClickEventProp.cache = {};
        }

        // check the cache
        var cached = this.getOnClickEventProp.cache[prop];
        if (!$A.util.isUndefined(cached)) {
            return cached;
        }

        // fill the cache
        this.getOnClickEventProp.cache["isTouchDevice"] = !$A.util.isUndefined(document.ontouchstart);
        if (this.getOnClickEventProp.cache["isTouchDevice"]) {
            this.getOnClickEventProp.cache["onClickStartEvent"] = "touchstart";
            this.getOnClickEventProp.cache["onClickEndEvent"] = "touchend";
        } else {
            this.getOnClickEventProp.cache["onClickStartEvent"] = "mousedown";
            this.getOnClickEventProp.cache["onClickEndEvent"] = "mouseup";
        }
        return this.getOnClickEventProp.cache[prop];
    },

    getOnClickStartFunction: function (component) {
        if ($A.util.isUndefined(component._onClickStartFunc)) {
            var helper = this;
            var f = function (event) {
                if (helper.getOnClickEventProp("isTouchDevice")) {
                    var touch = event.changedTouches[0];
                    // record the ID to ensure it's the same finger on a multi-touch device
                    component._onStartId = touch.identifier;
                    component._onStartX = touch.clientX;
                    component._onStartY = touch.clientY;
                } else {
                    component._onStartX = event.clientX;
                    component._onStartY = event.clientY;
                }
            };
            component._onClickStartFunc = f;
        }
        return component._onClickStartFunc;
    },

    handleDataChange: function (component, event) {
        var concreteCmp = component.getConcreteComponent();

        // Refactor this component:
        // We want to update the internal v.items, but without udating iteration just yet
        // since customer might have thir own matchText function
        concreteCmp.set("v.items", event.getParam("data"), true/*ignore changes, dont notify*/);

        this.matchText(concreteCmp, event.getParam("data"));
    },

    handleEsckeydown: function (component) {
        component.set("v.visible", false);
    },

    handleKeydown: function (component, event) {
        var keyCode = event.keyCode;
        if (keyCode === 39 || keyCode === 40) {  // right or down arrow key
            event.preventDefault();
            this.setFocusToNextItem(component, event);
        } else if (keyCode === 37 || keyCode === 38) {  // left or up arrow key
            event.preventDefault();
            this.setFocusToPreviousItem(component, event);
        } else if (keyCode === 27) {  // Esc key
            event.stopPropagation();
            this.handleEsckeydown(component, event);
        } else if (keyCode === 9) {  // tab key: dismiss the list
            this.handleTabkeydown(component, event);
        }
    },

    handleListHighlight: function (component, event) {
        var selectedSection = this.createKeyboardTraversalList(component);
        if (selectedSection) {
            var direction = event.getParam("activeIndex");
            selectedSection.deselect();
            if (direction < 0) { // highlight previous visible option
                selectedSection = selectedSection.decrement();
            } else { // highlight next visible option
                selectedSection = selectedSection.increment();
            }
            selectedSection.select(component);
        }
    },

    /* create a keyboard traversal list to simplify the logic of handling up/down keystrokes on the list */
    /* looks like this:  [header] <-> [items] <-> [footer] where header and footer are optional */
    createKeyboardTraversalList: function (component) {
        var selectedSection = null;
        var topSection = null;
        var bottomSection = null;
        var self = this;

        //create section for autocomplete list rows
        var itemsSection = this._createKeyboardTraversalItemsSection(component);
        var iterCmp = component.find("iter");
        if (!iterCmp) {
            return null;
        }
        itemsSection.iters = iterCmp.get("v.body");
        itemsSection.originalIndex = itemsSection.highlightedIndex = this.findHighlightedOptionIndex(itemsSection.iters);
        itemsSection.previous = itemsSection.next = topSection = bottomSection = itemsSection;
        if (itemsSection.highlightedIndex > -1) {
            selectedSection = itemsSection;
        }

        //create section for autocomplete header
        var header = this.getHeader(component);
        if (header && component.get("v.showListHeader")) {
            var headerSection = this._createBasicKeyboardTraversalSection();
            headerSection.deselect = function () {
                component.set("v.headerSelected", false);
                $A.util.removeClass(header, "highlighted");
            };
            headerSection.select = function () {
                component.set("v.headerSelected", true);
                $A.util.addClass(header, "highlighted");
                self.updateAriaAttributesFromIdAttribute(component, header);
            };
            headerSection.next = headerSection.previous = itemsSection;
            topSection = itemsSection.next = itemsSection.previous = headerSection;
            if (!selectedSection && component.get("v.headerSelected")) {
                selectedSection = headerSection;
            }
        }

        //create section for autocomplete footer
        var footer = this.getFooter(component);
        if (footer && component.get("v.showListFooter")) {
            var footerSection = this._createBasicKeyboardTraversalSection();
            footerSection.deselect = function () {
                component.set("v.footerSelected", false);
                $A.util.removeClass(footer, "highlighted");
            };
            footerSection.select = function () {
                component.set("v.footerSelected", true);
                $A.util.addClass(footer, "highlighted");
                self.updateAriaAttributesFromIdAttribute(component, footer);
            };
            footerSection.next = topSection;
            footerSection.previous = itemsSection;
            bottomSection = itemsSection.next = topSection.previous = footerSection;
            if (!selectedSection && component.get("v.footerSelected")) {
                selectedSection = footerSection;
            }
        }

        //create an empty section for when nothing is selected
        if (!selectedSection) {
            selectedSection = this._createBasicKeyboardTraversalSection();
            selectedSection.previous = bottomSection;
            selectedSection.next = topSection;
        }
        return selectedSection;
    },

    _createBasicKeyboardTraversalSection: function () {
        return {
            increment: function () {
                return this.next.incrementedTo();
            },
            decrement: function () {
                return this.previous.decrementedTo();
            },
            incrementedTo: function () {
                return this;
            },
            decrementedTo: function () {
                return this;
            },
            deselect: function () { /*do nothing*/
            },
            select: function () {  /*do nothing*/
            }
        };
    },

    _createKeyboardTraversalItemsSection: function (cmp) {
        var self = this;
        return {
            visited : false,
            increment: function () {
                var resultSection = this;
                if (!this.visited || this.highlightedIndex+1 <= this.originalIndex ) { //avoid infinite looping
                    this.highlightedIndex++;
                    if (this.highlightedIndex >= this.iters.length) {
                        this.highlightedIndex = -1;
                        this.visited = true;
                        resultSection = this.next.incrementedTo();
                    } else if (!this.iters[this.highlightedIndex].get("v.visible")) {
                        resultSection = this.incrementedTo();
                    }
                }
                return resultSection;
            },

            decrement: function () {
                var resultSection = this;
                if (!this.visited || this.highlightedIndex-1 >= this.originalIndex ) { //avoid infinite looping
                    if (this.highlightedIndex === -1) {
                        this.highlightedIndex = this.iters.length;
                    }
                    this.highlightedIndex--;
                    if (this.highlightedIndex < 0) {
                        this.highlightedIndex = -1;
                        this.visited = true;
                        resultSection = this.previous.decrementedTo();
                    } else if (!this.iters[this.highlightedIndex].get("v.visible")) {
                        resultSection = this.decrementedTo();
                    }
                }
                return resultSection;
            },

            incrementedTo: function () {
                return this.increment();
            },

            decrementedTo: function () {
                return this.decrement();
            },

            deselect: function () {
                if (this.highlightedIndex !== -1) {
                    this.iters[this.highlightedIndex].set("v.highlighted", false);
                }
            },

            select: function () {
                if (this.highlightedIndex === -1) {
                    $A.warning("Can't select item without index");
                    return;
                }
                var highlightedCmp = this.iters[this.highlightedIndex];
                highlightedCmp.set("v.highlighted", true);
                var highlightedElement = highlightedCmp.getElement();
                if (highlightedElement) {
                    if (highlightedElement.scrollIntoViewIfNeeded) {
                        highlightedElement.scrollIntoViewIfNeeded();
                    } else {
                        highlightedElement.scrollIntoView(false);
                    }
                }
                self.updateAriaAttributes(cmp, highlightedCmp);
            }

        };
    },

    addHeaderAndFooterClassesAndAttributes: function (component) {
        var header = this._getComponentByAttribute(component, "v.listHeader");
        if (header && header.getElement()) {
            $A.util.addClass(header, "lookup__header");
            header.getElement().setAttribute("id", header.getGlobalId());
            header.getElement().setAttribute("role", "option");
        }
        var footer = this._getComponentByAttribute(component, "v.listFooter");
        if (footer && footer.getElement()) {
            $A.util.addClass(footer, "lookup__footer");
            footer.getElement().setAttribute("id", footer.getGlobalId());
            footer.getElement().setAttribute("role", "option");
        }
    },

    getHeader: function (component) {
        return this._getComponentByAttribute(component, "v.listHeader");
    },

    getFooter: function (component) {
        return this._getComponentByAttribute(component, "v.listFooter");
    },

    _getComponentElementByAttribute: function (component, attribute) {
        return this._getComponentByAttribute(component, attribute).getElement();
    },

    _getComponentByAttribute: function (component, attribute) {
        var resultCmp = component.get(attribute);
        if ($A.util.isEmpty(resultCmp)) {
            return false;
        }
        if ($A.util.isArray(resultCmp)) {
            resultCmp = resultCmp[0];
        }
        return resultCmp;
    },


    handlePressOnHighlighted: function (component) {
        var iterCmp = component.find("iter");
        if (iterCmp) {
            var iters = iterCmp.get("v.body");
            var highlightedIndex = this.findHighlightedOptionIndex(iters);
            if (highlightedIndex >= 0) {
                var targetCmp = iters[highlightedIndex];
                var selectEvt = component.get("e.selectListOption");
                selectEvt.setParams({
                    option: targetCmp
                });
                selectEvt.fire();
            }
        }
    },

    handleTabkeydown: function (component) {
        component.set("v.visible", false);
    },

    hasVisibleOption: function (items) {
        var hasVisibleOption = false;
        for (var i = 0; i < items.length; i++) {
            if (items[i].visible === true) {
                hasVisibleOption = true;
                break;
            }
        }

        return hasVisibleOption;
    },

    /**
     * Checks if the object is an HTML element.
     * @param {Object} obj
     * @returns {Boolean} True if the object is an HTMLElement object, or false otherwise.
     */
    isHTMLElement: function (obj) {
        if (typeof HTMLElement === "object") {
            return obj instanceof HTMLElement;
        } else {
            return typeof obj === "object" && obj.nodeType === 1 && typeof obj.nodeName === "string";
        }
    },

    matchFunc: function (component, items) {
        items = items || component.get('v.items');
        var keyword = component.get("v.keyword");
        var propertyToMatch = component.get("v.propertyToMatch");
        var regex;
        try {
            regex = new RegExp(keyword, "i");
            for (var j = 0; j < items.length; j++) {
                items[j].keyword = keyword;
                var label = items[j][propertyToMatch];
                var searchResult = regex.exec(label);
                if (searchResult && searchResult[0].length > 0) { // Has a match
                    items[j].visible = true;
                } else {
                    items[j].visible = false;
                }
            }
        } catch (e) { // if keyword is not a legal regular expression, don't show anything
            for (var i = 0; i < items.length; i++) {
                items[i].keyword = keyword;
                items[i].visible = false;
            }
        }
    },

    matchFuncDone: function (component, items) {
        items = items || component.get('v.items');
        this.fireMatchDoneEvent(component, items);
        this.toggleListVisibility(component, items);
        this.showLoading(component, false);

        // Finally we update the v.items so iteration can 
        // create the final items here.
        component.set("v.items", items);

        //this.updateEmptyListContent(component);
        //JBUCH: HALO: HACK: WTF: FIXME THIS WHOLE COMPONENT
        var itemCmps = component.find("iter").get("v.body");
        for (var i = 0; i < itemCmps.length; i++) {
            $A.util.toggleClass(itemCmps[i], "force");
        }


    },

    matchText: function (component, items) {
        var action = component.get("v.matchFunc");
        if (action) {
            action.setCallback(this, function() {
                //@dval: Refactor all this nonsense:
                // - it should not be an action but js function
                // - it should not have the responsability to set the items directly
                // - we should not fire yet another stupid event since we are in the callback

                //this.matchFunc(component, items);
                this.matchFuncDone(component, items);
            });
            action.setParams({items: items});
            $A.enqueueAction(action);
        } else {
            this.matchFunc(component, items);
            this.matchFuncDone(component, items);
        }
    },

    toggleListVisibility: function (component, items) {
        var showEmptyListContent = !$A.util.isEmpty(component.get("v.emptyListContent")) && !$A.util.isEmpty(component.get("v.keyword"));

        var hasVisibleOption = this.hasVisibleOption(items);

        // Should no longer be necessary, as the class attribute is now adds "visible" if v.visible is true.
        //var list = component.find("list");
        //$A.util.toggleClass(list, "visible", hasVisibleOption);

        component.set("v.visible", hasVisibleOption || showEmptyListContent);
    },

    updateAriaAttributesFromIdAttribute: function (component, highlightedCmp) {
        var highlightedElement = highlightedCmp.getElement();
        if (highlightedElement) {
            var updateAriaEvt = component.get("e.updateAriaAttributes");
            if (updateAriaEvt) {
                updateAriaEvt.setParams({
                    attrs: { "aria-activedescendant": highlightedElement.getAttribute("id") }
                });
                updateAriaEvt.fire();
            }
        }
    },

    updateAriaAttributes: function (component, highlightedCmp) {
        var updateAriaEvt = component.get("e.updateAriaAttributes");
        if (updateAriaEvt) {
            var obj = {
                "aria-activedescendant": highlightedCmp.get("v.domId")
            };
            updateAriaEvt.setParams({
                attrs: obj
            });
            updateAriaEvt.fire();
        }
    },

    updateEmptyListContent: function (component) {
        var visible = component.getConcreteComponent().get("v.visible");
        var items = component.getConcreteComponent().get("v.items");
        var hasVisibleOption = this.hasVisibleOption(items);

        $A.util.toggleClass(component, "showEmptyContent", visible && !hasVisibleOption);
    },

    showLoading: function (component, visible) {
        $A.util.toggleClass(component, "loading", visible);

        // Originally, no loading indicator was shown. Making it only appear when specified in the facet.
        if (!$A.util.isEmpty(component.get("v.loadingIndicator"))) {
            var list = component.find("list");
            $A.util.toggleClass(list, "invisible", !visible);
        }
    },
    
    setDefaultHighlight: function(component) {
    	var setDefaultHighlight = component.get("v.setDefaultHighlight");
    	var visible = component.get("v.visible");
    	if (setDefaultHighlight !== true || visible !== true) {
    		return; 
    	}
    	var iterCmp = component.find("iter");
    	if (iterCmp) {
    		var iters = iterCmp.get("v.body");
    		var found = false;
    	    for (var i = 0; i < iters.length; i++) {
                var optionCmp = iters[i];
                if (found === false && optionCmp.get("v.visible") === true) {
                    optionCmp.set("v.highlighted", true);
                    this.updateAriaAttributes(component, optionCmp);
                    found = true;
                } else {
                	optionCmp.set("v.highlighted", false);
                }
            }
    	}
    },
    
    setUpEvents: function (component) {
        if (component.isRendered()) {
            var obj = {};
            var visible = component.get("v.visible");

            // Should no longer be necessary. We do this in an expression now on the list
            //var list = component.find("list");
            //$A.util.toggleClass(list, "visible", visible);

            // auto complete list is hidden.
            if (visible === false) {
                // Remove loading indicator
                obj["aria-activedescendant"] = "";
                obj["aria-expanded"] = false;
                // De-register list expand/collapse events
                $A.util.removeOn(document.body, this.getOnClickEventProp("onClickStartEvent"), this.getOnClickStartFunction(component));
                $A.util.removeOn(document.body, this.getOnClickEventProp("onClickEndEvent"), this.getOnClickEndFunction(component));
            } else { // Register list expand/collapse events
                obj["aria-expanded"] = true;
                $A.util.on(document.body, this.getOnClickEventProp("onClickStartEvent"), this.getOnClickStartFunction(component));
                $A.util.on(document.body, this.getOnClickEventProp("onClickEndEvent"), this.getOnClickEndFunction(component));

                //push this even to the end of the queue to ensure that the interation in the component body is complete
                window.setTimeout($A.getCallback(function () {
                    if (component.isValid()) {
                        component.get("e.listExpand").fire();
                    }
                }, 0));

            }


            // Update accessibility attributes
            var updateAriaEvt = component.get("e.updateAriaAttributes");
            if (updateAriaEvt) {
                updateAriaEvt.setParams({
                    attrs: obj
                });
                updateAriaEvt.fire();
            }
        }
    }
})// eslint-disable-line semi

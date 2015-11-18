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

    INPUT_MIN_WIDTH_PX: 100,
    SCROLL_BAR_MARGIN_PX: 20,

    handleItemSelected: function(cmp, newItemList) {
        if (!$A.util.isEmpty(newItemList) && $A.util.isArray(newItemList)) {
            this.insertItems(cmp, newItemList);


            // when we've reached max pills
            // allow the iteration to rerender the pills before setting focus to th last pill
            // this only happens when pill selection comes from input
            var itemsLength = cmp.get("v.items").length;
            var maxAllowed = cmp.get("v.maxAllowed");
            if (itemsLength >= maxAllowed) {
                window.setTimeout(function () {
                    $A.run(function () {
                        if (cmp.isValid()) {
                            var pillItems = cmp.find('pill');
                            if (!$A.util.isEmpty(pillItems)) {
                                if ($A.util.isArray(pillItems)) {
                                    pillItems[pillItems.length - 1].focus();
                                } else {
                                    pillItems.focus();
                                }
                            }
                        }
                    });
                }, 0);
            }
        }
    },

    insertItems: function(cmp, newItems){
        var curItems = cmp.get('v.items');
        var itemsIsUpdated = false;
        // 1.) Check if max has been reached. If so then don't try to insert.
        // 2.) Check to see if newItems[i] exists, if it does don't add it
        for (var i = 0; i < newItems.length; i++) {
            if (this._hasReachedMax(cmp, curItems)) {
                break;
            }
            if (!newItems[i].hasOwnProperty("label")) {
                throw new $A.auraError("Can't insert pill. New pill items must have label property.");
            }
            if (!this._itemExists(curItems, newItems[i])) {
                itemsIsUpdated = true;
                curItems.push(newItems[i]);
                cmp.get("e.pillInserted").fire();
            }
        }

        if (itemsIsUpdated) {
            cmp.set('v.items', curItems);
        }
        this.focusOnInputBox(cmp);  // Always focus on the input after adding new pill (W-2529162)
    },

    updateDisplayedItems: function(cmp){
        var items = cmp.get("v.items");
        var maxAllowed = cmp.get("v.maxAllowed");

        //truncate list
        if (items.length > maxAllowed) {
            items.splice(maxAllowed-1, items.length - maxAllowed);
            cmp.set("v.items", items);
        }

        if (items.length === maxAllowed) {
            $A.util.addClass(cmp.getElement(), 'maxAllowed');
        } else {
            $A.util.removeClass(cmp.getElement(), 'maxAllowed');
        }
        this._setActiveItem(cmp, true);
    },

    handlePillEvent: function(cmp, event) {
        var params = event.getParams();
        var action = params.value.action;
        var data = {id: params.value.id, label: params.value.label};
        if (!action) {
            return;
        }
        if (action === 'focusPrevItem') {
            this.focusItem(cmp, data, -1);
        } else if (action === 'focusNextItem') {
            this.focusItem(cmp, data, 1);
        } else if (action === 'focusLastItem') {
            this.focusItem(cmp, data, 0);
        } else if (action === 'delete') {
            this.deleteItem(cmp, data);
        }
    },

    deleteItem: function(cmp, data) {
        if (cmp.get("v.allowRemove")) {
            // There is domEventHandlers attached to the pills that are invoked on afterRender and rerender.
            // Need to let aura run its course so that lingering handlers are invoked before its safe to update the list.
            var that = this;
            window.setTimeout(function () {
                $A.run(function () {
                    if (cmp.isValid()) {
                        that._deleteItem(cmp, data);
                    }
                });
            }, 0);
        }
    },

    /**
     * Move focus of the pill list to prev or next depending on direction
     * @param {Component} cmp this component.
     * @param {data} the data of the currently focused item
     * @param {direction} -1 for focusing on prev item, 1 for focusing on next and 0 for focusing on last
     */
    focusItem: function(cmp, data, direction) {

        if (isNaN(direction)) {
            return;
        }

        var items = cmp.find('pill');
        if ($A.util.isUndefinedOrNull(items)) {
            return;
        }

        //ensure we have an array
        var itemsArray = $A.util.isArray(items) ? items : [items];
        var index = 0;

        //find the current index
        if (direction === 0) {
            index = itemsArray.length - 1;
        } else {
            for (var i = 0; i < itemsArray.length; i++) {
                if (this._isEqual(data, {id:itemsArray[i].get('v.id'),label:itemsArray[i].get('v.label')})) {
                    index = (i + direction) ;
                    break;
                }
            }
        }

        // If the next item index is greater then length of list then focus on the input
        // If the input doesn't exist we wrap around to the beginning of the list
        // If index is < 0 then it means we wrap around to the last item
        if (index >= itemsArray.length) {
            if (!this.focusOnInputBox(cmp)) {
                itemsArray[0].focus();
            }
        } else if (index < 0) {
            itemsArray[itemsArray.length - 1].focus();
        } else {
            itemsArray[index].focus();
        }
    },

    focusOnInputBox: function(cmp) {
        var focused = false;
        var pillInput = cmp.get("v.pillInput");
        var pillItemData = cmp.get("v.items");
        var itemsLength = pillItemData.length;
        var maxAllowed = cmp.get("v.maxAllowed");
        if ($A.util.isEmpty(pillInput)) {
            $A.util.addClass(cmp.getElement(), 'noinput');
        } else if (itemsLength<maxAllowed) {
            pillInput[0].focus();
            focused = true;
        }
        if (!focused && itemsLength > 0) { // If there is any pill data present
            this.focusItem(cmp, pillItemData, 0); // Focus on the last pill
        }
        return focused;
    },

    adjustHeight: function(cmp) {
        if (!cmp.get("v.expanded")) {
            var hideShowMore = true;
            var maxLines = cmp.get("v.maxLines");
            if (maxLines > 0) {
                var listItems = cmp.find("listitem");
                if (!$A.util.isEmpty(listItems)) {

                    //find the height of a pill
                    var firstItem;
                    var lastItem;
                    if ($A.util.isArray(listItems)) {
                        firstItem = listItems[0].getElement();
                        lastItem = listItems[listItems.length - 1].getElement();
                    } else {
                        lastItem = firstItem = listItems.getElement();
                    }
                    if (firstItem) {
                    	var pillHeight = this._getActualHeight(firstItem);

                        //set the maximum height of the pill container based on maxLines attribute
                        var list = cmp.find("list");
                        var limitedHeight = pillHeight * maxLines;
                        list.getElement().style.maxHeight = limitedHeight + "px";

                        //only show the Show More button if there's overflow
                        var lastItemBottom = lastItem.offsetTop - list.getElement().offsetTop + pillHeight;
                        if (lastItemBottom > limitedHeight) {
                            hideShowMore = false;
                        }
                    }
                }
            }
            if (hideShowMore) {
                $A.util.addClass(cmp.find("showMore").getElement(), 'invisible');
            } else {
                $A.util.removeClass(cmp.find("showMore").getElement(), 'invisible');
            }
        }
    },

    showMore: function(cmp) {
        var list = cmp.find("list");
        list.getElement().style.maxHeight = "";
        $A.util.addClass(cmp.find("showMore").getElement(), 'invisible');
        cmp.set("v.expanded", true);

    },

    _getActualHeight: function(element) {
        var elmHeight, elmMargin;
        if (document.all) { // IE
            elmHeight = element.currentStyle.height;
            elmMargin = parseInt(element.currentStyle.marginTop, 10) + parseInt(element.currentStyle.marginBottom, 10);
        }
        else {
            var computedStyle = getComputedStyle(element, null);
            elmHeight = parseInt(computedStyle.height);
            elmMargin = parseInt(computedStyle.marginTop) + parseInt(computedStyle.marginBottom);
        }
        return elmHeight + elmMargin;
    },

    _itemExists: function(itemsList, newItem) {
        var found = false;
        for(var i=0;i < itemsList.length;i++) {
            if(this._isEqual(itemsList[i], newItem)) {
                found = true;
                break;
            }
        }
        return found;
    },

    _hasReachedMax: function(cmp, items) {
        return (items.length >= cmp.get("v.maxAllowed"));
    },

    _isEqual: function(object1, object2) {
        //todo: comparing on id makes more sense
        return (object1.label.toLowerCase() === object2.label.toLowerCase());
    },

    _deleteItem: function(cmp, data) {
        var items = cmp.get('v.items'),
            itemsLength = items.length;

        for (var i = 0; i < itemsLength; i++) {
            if (this._isEqual(data, items[i])) {
                // remove found item
                items.splice(i, 1);
                cmp.set('v.items', items);

                cmp.get("e.pillRemoved").fire();
                if (items.length <= 0) {
                    this.focusOnInputBox(cmp);
                } else {
                    // need to set focus on next item
                    var pillItems = cmp.find('pill');
                    pillItems.splice(i, 1);
                    if ($A.util.isEmpty(items) || !$A.util.isArray(items)) {
                        return;
                    }
                    var idxToFocus = i; // [i] is removed, so next one is i
                    // correct for pointing to last item
                    if (idxToFocus >= pillItems.length) {
                        idxToFocus = pillItems.length - 1;
                    }

                    pillItems[idxToFocus].focus();
                }
                this._setActiveItem(cmp, true);
                break;
            }
        }
    },

    _setActiveItem: function(cmp, firstActive) {
        var pillItems = cmp.find('pill');
        if (pillItems) {
            if (!$A.util.isArray(pillItems)) {
                pillItems.set("v.active", true);
            } else {
                pillItems[0].set("v.active", firstActive);
                pillItems[pillItems.length - 1].set("v.active", !firstActive);
            }
        }
    }

})// eslint-disable-line semi

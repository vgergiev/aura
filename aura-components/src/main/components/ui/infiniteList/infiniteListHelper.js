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
    DIRECTION_THRESHOLD : 2,   // the intent of a gesture is captured after moving 2px in a direction
    OPEN_PERCENTAGE     : 80,  // an 'open' row is translated 80% from its initial position
    COMMIT_PERCENTAGE   : 20,  // the minimum absolute distance necessary to force commitment of either opening or closing
    SNAP_TIMEOUT        : 300, // duration of 'snap' transition
    CLOSE_TIMEOUT       : 600, // duration of the full 'close' transition of an active row

    /**
     * Creates the handlers needed for listening to touch/pointer events.
     */
    initializeHandlers: function (cmp) {
        var self = this;

        cmp._ontouchstart = function (e) {
            self.ontouchstart(cmp, e);
        };

        cmp._ontouchmove = function (e) {
            self.ontouchmove(cmp, e);
        };

        cmp._ontouchend = function (e) {
            self.ontouchend(cmp, e);
        };

        cmp._preventEvent = function(e) {
            e.stopPropagation();
            e.preventDefault();
        };

        cmp._ontouchcancel = function(e) {
            self.ontouchcancel(cmp, e);
        };

        cmp._onInfiniteListRowOpen = function () {
            // 1. close the open row if it exists
            // 2. make the target row the open row
            // 3. open the row with a transition (if desired, check for e.details.useTransition
            $A.warning('List row open is unimplemented.');
        };

        cmp._onInfiniteListRowClose = function (e) {
            self.onInfiniteListRowClose(cmp, e);
        };
    },

    /**
     * Concats the new items to the list of existing items.
     *
     * @param {Component} cmp Potentially non-concrete (ui:abstractList) component instance.
     * @param evt ui:dataChanged.
     * @param {Function} callback An optional callback to invoke after 'v.items' has been replaced.
     */
    handleDataChange: function(cmp, evt, callback) {

        var concrete      = cmp.getConcreteComponent(),
            newData       = evt.getParam("data") || [],
            targetPage    = evt.getParam("currentPage"),
            hasTargetPage = $A.util.isFiniteNumber(targetPage),
            pageSize      = concrete.get("v.pageSize"),
            currentPage   = concrete.get("v.currentPage"),
            actualItems   = concrete.get("v.items") || [],
            len, i, j;

        if (hasTargetPage) {
            var offset = (targetPage - 1) * pageSize;
            for (i = 0, len = newData.length; i < len; i++) {
                actualItems[offset++] = newData[i];
            }

            // Remove left over old data (the length of new data is less than that of the old data)
            if (targetPage === currentPage && offset < actualItems.length) {
                actualItems.splice(offset, actualItems.length);
            }
        } else { // If we don't know which page the data is for, just append to the end.
            for (j = 0, len = newData.length; j < len; j++) {
                actualItems.push(newData[j]);
            }
        }

        concrete.set("v.items", actualItems);

        if (callback) {
            callback();
        }
    },

    /**
     * Attaches event listeners to their handlers.
     */
    attachListeners: function (cmp) {
        var ul = cmp.getElement();
        ul.addEventListener(this.getEventNames().move, cmp._ontouchmove, false);
        ul.addEventListener(this.getEventNames().end, cmp._ontouchend, true);
        ul.addEventListener(this.getEventNames().cancel, cmp._ontouchcancel, false);
        ul.addEventListener("dragstart", cmp._preventEvent, true);
    },

    /**
     * Detaches event listeners to their handlers.
     */
    detachListeners: function (cmp) {
        var ul = cmp.getElement();
        ul.removeEventListener(this.getEventNames().move, cmp._ontouchmove);
        ul.removeEventListener(this.getEventNames().end, cmp._ontouchend, true);
        ul.removeEventListener(this.getEventNames().cancel, cmp._ontouchcancel, false);
        ul.removeEventListener("dragstart", cmp._preventEvent, true);
    },

    /**
     * 'start' handler.
     *  Attempts to resolve the actionable row and
     *  attaches 'move' and 'end' listeners if a valid row was found.
     */
    ontouchstart: function (cmp, e) {
        var touch, rootClassName, row,
            initialPosition = 0;

        // Cancel when blocking is needed.
        if (cmp._isClosing || cmp._isSnapping || this.isBlocked(cmp)) {
            e.stopPropagation();
            e.preventDefault();
            return;
        }

        if ((e.touches && e.touches.length === 1) || (e.pageX !== undefined)) {
            touch = (e.touches && e.touches[0]) || e;
            rootClassName = cmp.getElement().className || 'uiInfiniteList';
            row = this.getRow(e.target, 'uiInfiniteListRow', rootClassName);

            // Only proceed if a valid row was found.
            if (row) {
                this.attachListeners(cmp);

                // Detect whether this interaction is on the open row.
                cmp._isInteractionOnOpenRow = cmp._openRow && cmp._previousSwipe && (cmp._previousSwipe.row === row);

                // If a different row was open, close it. Slightly different logic than above.
                if (cmp._openRow && cmp._previousSwipe && (cmp._previousSwipe.row !== row)) {
                    // Cancel all further events - this handler is registered in the 'capture' phase.
                    e.stopPropagation();
                    e.preventDefault();

                    // Close the row,
                    this.fireRowClose(cmp, cmp._openRow);

                    // body doesn't always exist here...
                    if (!cmp._previousSwipe.body) {
                        cmp._previousSwipe.body = cmp._previousSwipe.row.querySelector('.body');
                    }
                    this.closeRowBlockAndReset(cmp, cmp._previousSwipe.body, true);

                    return;
                }

                // If another swipe is occurring on the same row. Check for openness.
                if (cmp._previousSwipe && (cmp._previousSwipe.row === row)) {
                    if (cmp._openRow === row) {
                        initialPosition = this.getPixelFromPercentage(cmp, row, this.OPEN_PERCENTAGE);
                    }
                }

                // Begin tracking the swipe gesture.
                cmp._swipe = {
                    row                : row,
                    startX           : touch.pageX,
                    startY             : touch.pageY,
                    initialPosition : initialPosition
                };
                $A.util.addClass(row, 'swiping');
            }
        }
    },

    ontouchcancel: function(cmp) {
        var swipe = cmp._swipe;

        if (swipe) {
            var body = swipe.body || swipe.row.querySelector('.body');
            this.closeRowBlockAndReset(cmp, body, true);
        }
    },

    /**
     * 'move' handler.
     * Records the touch/pointer interaction.
     */
    ontouchmove: function (cmp, e) {
        var point = null, // must be explicitly null
            swipe, axis, percentage;

        // If a row is closing or the interaction has been blocked,
        // bounce the event and return.
        if (cmp._isClosing || cmp._isSnapping || this.isBlocked(cmp)) {
            e.stopPropagation();
            e.preventDefault();
            return;
        }

        // Continue tracking the swipe if the an associated row was found.
        if (cmp._swipe && cmp._swipe.row && (point = this.getPoint(e)) !== null) {
            swipe = cmp._swipe;

            // Records the most recent point in the movement.
            // Calculates the diffs in horizontal (X) and vertical (Y) position.
            swipe.x     = point.x;
            swipe.y     = point.y;
            swipe.diffX = (swipe.startX - point.x);
            swipe.diffY = (swipe.startY - point.y);
            swipe.absX     = Math.abs(swipe.diffX);
            swipe.absY     = Math.abs(swipe.diffY);
            swipe.body  = swipe.body || swipe.row.querySelector('.body');

            // Lock the direction for the duration of the iteraction.
            axis = cmp._direction || this.getScrollAxis(swipe.absX, swipe.absY);

            if (axis === 'x') {
                // If a greater gesture occurred horizontally than vertically,
                // then prevent prevent the scroller from moving.
                // Tells all scrollers to cancel scrolling (see ScrollerJS.js)
                event.cancelScrolling = true;

                // Positive displacement is a 'open' gesture.
                // Negative displacement is an 'close' gesture.
                if (swipe.diffX > 0) {
                    percentage = this.getWidthPercentage(cmp, swipe.body, swipe.absX + swipe.initialPosition);
                    swipe.percentage = -percentage;

                    if (swipe.percentage >= -(this.OPEN_PERCENTAGE)) {
                        this.translateX(cmp, swipe.body, swipe.percentage);
                    }
                }
                else {
                    percentage = this.getWidthPercentage(cmp, swipe.body, (swipe.absX + (cmp._bodyLength - swipe.initialPosition)));
                    swipe.percentage = -this.getWidthPercentage(cmp, swipe.body, swipe.absX);

                    if (cmp._isInteractionOnOpenRow && swipe.percentage <= 0 && percentage <= 100) {
                        this.translateX(cmp, swipe.body, -(100 - percentage));
                    }
                }
            }
            else if (cmp._isInteractionOnOpenRow) {
                // Cancel all further events if we have movement on the y-axis.
                e.stopPropagation();
                e.preventDefault();
            }
        }
    },

    /**
     * 'end' handler.
     * Determines if the movements is considered a swipe.
     * Detaches event listeners.
     */
    ontouchend: function (cmp, e) {
        var swipe = cmp._swipe,
            percentage, shouldSnapOpen;

        // Use 'percentage' field on 'swipe' to determine the position of the row.
        if (swipe && swipe.hasOwnProperty('percentage')) {
            percentage = Math.abs(swipe.percentage);

            // If the row is not completely open or not completely closed, then apply 'snap' logic.
            if (percentage !== -(this.OPEN_PERCENTAGE) && percentage !== 0) {

                swipe.body.style.transition = 'all ' + this.SNAP_TIMEOUT + 'ms';

                // Block interactions while the transition is happening.
                cmp._isSnapping = true;

                // If the absolute percentage moved meets the threshold, then commit to either opening or closing.
                var commit = percentage >= this.COMMIT_PERCENTAGE;
                var rightSwipe = swipe.diffX > 0;
                if ((commit && rightSwipe) || (!commit && !rightSwipe)) {
                    this.translateX(cmp, swipe.body, -(this.OPEN_PERCENTAGE));

                    // Create '_openRow' reference after timeout 'snap' has completed.
                    // Creating the reference too soon could cause a 'close' animation to also occur.
                    shouldSnapOpen = true;

                    if (!$A.util.hasClass(swipe.row, 'open')) {
                        this.fireRowOpen(cmp, swipe.row);
                    }
                } else {
                    this.translateX(cmp, swipe.body, 0);

                    if ($A.util.hasClass(cmp._openRow, 'open'))  {
                        this.fireRowClose(cmp, cmp._openRow);
                        $A.util.removeClass(cmp._openRow, 'open');
                    }

                    cmp._openRow = null;
                }

                this.setCheckedTimeout(cmp, function () {
                    cmp._isSnapping = false;
                    swipe.body.style.transition = '';
                    $A.util.removeClass(swipe.row, 'swiping');

                    if (shouldSnapOpen) {
                        cmp._openRow = swipe.row;
                        $A.util.addClass(cmp._openRow, 'open');
                    }
                }, this.SNAP_TIMEOUT);
            }

            // Prevent anything else from happening (clicks, etc).
            e.stopPropagation();
            e.preventDefault();

        // If the interaction wasn't a swipe, but was on the body of the
        // open row, we should close and reset the row
        } else if (cmp._isInteractionOnOpenRow) {
            var body = this.getRow(e.target, 'body', cmp.getElement().className || 'uiInfiniteList');
            if (body) {
                // Cancel all further events - this handler is registered in the 'capture' phase.
                e.stopPropagation();
                e.preventDefault();

                // Close the row,
                this.fireRowClose(cmp, cmp._openRow);
                this.closeRowBlockAndReset(cmp, body, true);
            }
        }

        // Reset '_isBlockedInteraction' so that future touch events are not cancelled.
        // This is here because the touch gesture might last longer than the animation.
        // A 'blocked interaction' means that all pointer events are cancelled as long as
        // the pointer is active (down).
        if (this.isBlocked(cmp)) {
            e.stopPropagation();
            e.preventDefault();
            this.unblock(cmp);
        }

        this.detachListeners(cmp);

        cmp._previousSwipe = cmp._swipe;
        cmp._swipe = null;
        cmp._moved = false;
        cmp._direction = null;
        cmp._isInteractionOnOpenRow = null;
    },

    fireRowOpen: function(cmp, row) {
        cmp.get("e.onRowOpen").setParams({row: row}).fire();
    },

    fireRowClose: function(cmp, row) {
        cmp.get("e.onRowClose").setParams({row: row}).fire();
    },

    setCheckedTimeout: function(cmp, code, delay) {
        setTimeout(function() {
            if (cmp && cmp.isValid() && code) {
                code();
            }
        }, delay);
    },

    /**
     * Closes the current row.
     */
    onInfiniteListRowClose: function (cmp, e) {
        e.preventDefault();
        e.stopPropagation();

        var target           = e.target,
            body           = target.querySelector('div.body'),
            useTransition = e.detail && e.detail.useTransition;

        if (body && $A.util.hasClass(target, 'open')) {
            this.closeRowBlockAndReset(cmp, body, useTransition);
        }

        if (this.isBlocked(cmp)) {
            this.unblock(cmp);
        }
    },

    /**
     * Given an active swipe, close the row and reset.
     */
    closeRowBlockAndReset: function (cmp, body, useTransition) {
        // Perform close operation.
        if (body) {
            if (useTransition) {
                body.style.transition = 'all ' + this.CLOSE_TIMEOUT + 'ms';
            }
            this.translateX(cmp, body, 0);
        }

        // Null these fields as 'touchend' will not execute.
        $A.util.removeClass(cmp._openRow, 'open');
        $A.util.removeClass(cmp._openRow, 'swiping');
        cmp._openRow = null;
        cmp._swipe = null;

        // Change the state for the duration of the close animation.
        // Use two variables to cancel all touch events.
        cmp._isClosing = true;
        this.block(cmp);

        var self = this;

        this.setCheckedTimeout(cmp, function () {
            if (useTransition && body) {
                body.style.transition = '';
            }

            cmp._isClosing = false;
            self.unblock(cmp);
        }, this.CLOSE_TIMEOUT);
    },

    isBlocked: function (cmp) {
        return cmp._isBlockedInteraction;
    },

    block: function (cmp) {
        cmp._isBlockedInteraction = true;
    },

    unblock: function (cmp) {
        cmp._isBlockedInteraction = false;
    },

    /**
     * Overridden implementation to remove a row with an optional animation timeout.
     */
    removeItem: function (component, array, index, timeout, animate, callback) {
        var row;

        function rm() {
            component.set('v.items', array);
        }

        // Animations require a specified timeout.
        if (animate && $A.util.isUndefinedOrNull(timeout)) {
            $A.warning("'animation' function specified to ui:infinteList WITHOUT the required timeout. Please specify a timeout in milliseconds.");
        }

        if (timeout) {
            if (animate) {
                row = this.getNthRow(component, index);
                animate(row);
            }

            this.setCheckedTimeout(component, function () {
                rm();

                if (callback) {
                    callback();
                }
            }, timeout);
        }
        else {
            rm();
        }
    },

    /**
     * Resolve event names due to device variance.
     */
    getEventNames: function () {
        var eventNames;

        if (this._eventNames) {
            return this._eventNames;
        }

        if (window["navigator"]["pointerEnabled"]) {
            eventNames = {
                start  : 'pointerdown',
                move   : 'pointermove',
                end    : 'pointerup',
                cancel : 'pointercancel'
            };

        }
        else if (window["navigator"]["msPointerEnabled"]) {
            eventNames = {
                start  : 'MSPointerDown',
                move   : 'MSPointerMove',
                end    : 'MSPointerUp',
                cancel : 'MSPointerCancel'
            };

        }
        else {
            eventNames = {
                start  : 'touchstart',
                move   : 'touchmove',
                end    : 'touchend',
                cancel : 'touchcancel'
            };
        }

        // Cache the event names on the helper.
        this._eventNames = eventNames;
        return eventNames;
    },

    /**
     * Normalize 'touch' and 'pointer' events.
     */
    getPoint: function (e) {
        var point = {};

        if (e.targetTouches) {
            point.x = e.targetTouches[0].clientX;
            point.y = e.targetTouches[0].clientY;
        }
        else {
            point.x = e.clientX;
            point.y = e.clientY;
        }

        return point;
    },

    /**
     * Given the absoluate value of movemnet in x and y, return the scrolling (dominant) axis.
     */
    getScrollAxis: function (absX, absY) {
        var treshold = this.DIRECTION_THRESHOLD;

        return (absX > absY + treshold) ? 'x' :
               (absY > absX + treshold) ? 'y' :
                null;
    },

    /**
     * Attempts to find a row given the current touch target.
     * @return {HTMLElement} If a row is found or null if otherwise.
     */
    getRow: function (el, targetClassName, rootClassName) {
        // Count prevents an infinite loop from occurring due to algorithm breakage.
        // God save you if you have 100 nested elements in your component.
        var count = 0,
            current = el,
            row = null;

        // Walk the tree until the closest target is found.
        // Escape if 100 nodes are traversed or the root is hit.
        while (count < 100 && current.className !== rootClassName) {
            if ($A.util.hasClass(current, targetClassName)) {
                row = current;
                break;
            }

            current = current.parentNode;
            ++count;
        }

        return row;
    },

    /**
     * Returns the nth concrete row element.
     */
    getNthRow: function (component, index) {
        var ul = component.getConcreteComponent().find('ul').getElement();
        return ul.children[index];
    },

    /**
     * Returns the percentage coverage given the absolute value of x distance.
     *
     * @param cmp {Component} infinteList component instance
     * @param el {HTMLElement} infiniteListRow's body div
     * @param x {Number} the number of pixels
     */
    getWidthPercentage: function (cmp, el, x) {
        var length = cmp._bodyLength;

        if (!length) {
            length = cmp._bodyLength = el.getBoundingClientRect().right;
        }

        return Math.floor((x / length) * 100);
    },

    /**
     * Returns the pixel position given the percent value.
     *
     * @param cmp {Component} infinteList component instance
     * @param el {HTMLElement} infiniteListRow's body div
     * @param pecentage {Number} integer value for percentage (eg. 80 for 80%).
     */
    getPixelFromPercentage: function (cmp, el, percentage) {
        var length = cmp._bodyLength;

        if (!length) {
            length = cmp._bodyLength = el.getBoundingClientRect().right;
        }

        return Math.floor(length * (percentage / 100));
    },

    /**
     * Translates the given element in the x direction by the given percent.
     *
     * @param percent {Number} percentage to apply
     */
    translateX: function (cmp, el, percent) {
        var style = 'translate3d(' + percent + '%, 0, 0)';
        el.style.transform = style;
        el.style.webkitTransform = style;
    }
})// eslint-disable-line semi
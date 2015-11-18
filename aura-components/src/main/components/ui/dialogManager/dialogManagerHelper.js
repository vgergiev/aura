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
     * Activates a single ui:dialog component by:
     *     1. setting a reference to it on the dialogManager's model.
     *     2. applying event handlers for proper interaction.
     *     3. setting a reference to those event handlers on the dialog's model (for removal later).
     *     4. applying CSS classes to display the dialog.
     *
     * @param {Aura.Component} dialog the ui:dialog component to activate
     * @param {Aura.Component} manager the ui:dialogManager component
     * @return {void}
     */
    activateDialog : function(dialog, manager) {
        var oldHandlerConfig   = dialog.get("v._handlerConfig");
        if ($A.util.isUndefinedOrNull(oldHandlerConfig)) {
            var isModal         = dialog.get("v.isModal"),
                clickOutToClose = dialog.get("v.clickOutToClose"),
                autoFocus       = dialog.get("v.autoFocus"),
                handlerConfig   = this.getHandlerConfig(dialog, isModal, clickOutToClose);
            this.applyHandlers(handlerConfig);
            this.toggleDisplay(true, dialog, autoFocus, isModal, handlerConfig);
            manager.set("v._activeDialog", dialog);
            dialog.set("v._handlerConfig", handlerConfig);
        }
    },


    /**
     * Deactivates a single ui:dialog component by:
     *     1. removing the reference to it from the dialogManager's model.
     *     2. removing event handlers.
     *     3. removing the reference to those event handlers from the dialog's model.
     *     4. removing CSS classes to hide the dialog.
     *
     * @param {Aura.Component} dialog the ui:dialog component to deactivate
     * @param {Aura.Component} manager the ui:dialogManager component
     * @return {void}
     */
    deactivateDialog : function(dialog, manager) {
        var isModal       = dialog.get("v.isModal"),
            autoFocus     = dialog.get("v.autoFocus"),
            handlerConfig = dialog.get("v._handlerConfig");
        if (!$A.util.isUndefinedOrNull(handlerConfig)) {
            this.removeHandlers(handlerConfig);
            this.toggleDisplay(false, dialog, autoFocus, isModal, handlerConfig);
            manager.set("v._activeDialog", null);
            dialog.set("v._handlerConfig", null);
        }
    },


    /**
     * Applies the appropriate event handlers for proper interaction.
     * 
     * @param {Object} config JS object that contains all the necessary event handlers
     * @return {void}
     */
    applyHandlers : function(config) {

        $A.util.on(document, "keydown", config.keydownHandler, false);
        $A.util.on(document, "click", config.clickHandler, false);
        $A.util.on(window, "resize", config.resizeHandler, false);

    },


    /**
     * Removes the appropriate event handlers to keep the DOM tidy.
     * 
     * @param {Object} config JS object that contains all the necessary event handlers
     * @return {void}
     */
    removeHandlers : function(config) {

        $A.util.removeOn(document, "keydown", config.keydownHandler, false);
        $A.util.removeOn(document, "click", config.clickHandler, false);
        $A.util.removeOn(window, "resize", config.resizeHandler, false);

    },


    /**
     * Builds the appropriate DOM event handlers necessary to interact with the
     * active ui:dialog component, as well as references to the document's previously
     * focused element (before the dialog opens), and the first focusable element
     * inside the dialog. This config object is stored on the dialog component's
     * model so we can remove the event handlers when the dialog is finally closed.
     * 
     * @param {Aura.Component} dialog the active ui:dialog component
     * @param {Boolean} isModal specifies if the active dialog is modal
     * @param {Boolean} clickOutToClose specifies if clicking outside the dialog should close it
     * @return {Object} JS config object w/ references to event handlers, as well as the elements to which we need to apply focus
     */
    getHandlerConfig : function(dialog, isModal, clickOutToClose) {

        var self          = this,
            oldFocus      = document.activeElement,
            newFocus      = this.getFirstFocusableElement(dialog),
            keydown       = function(event) { self.getKeydownHandler(dialog, isModal, newFocus, event); },
            click         = function(event) { self.getClickHandler(dialog, clickOutToClose, event); },
            resize        = function() { self.getResizeHandler(dialog, isModal); };

        return {
            oldFocus       : oldFocus,
            newFocus       : newFocus,
            keydownHandler : keydown,
            clickHandler   : click,
            resizeHandler  : resize
        };

    },


    /**
     * Constructs the handler for the DOM keydown event. Includes handlers for 1) escape key,
     * and 2) tab key (including shift+tab).
     * 
     * @param {Aura.Component} dialog the active ui:dialog component
     * @param {Boolean} isModal specifies if the dialog is modal
     * @param {HTMLElement} firstFocusable the first focusable element inside the dialog
     * @param {UIEvent} event DOM keydown event
     * @return {void}
     */
    getKeydownHandler : function(dialog, isModal, firstFocusable, event) {

        if (!event) { event = window.event; }

        var closeButton  = dialog.find("closeButton").getElement(),
            shiftPressed = event.shiftKey,
            currentFocus = document.activeElement,
            closeEvent   = $A.get("e.ui:closeDialog");

        closeEvent.setParams({ dialog : dialog, confirmClicked : false });

        switch (event.keyCode) {
            case 27: // escape key - always closes all dialogs
                $A.util.squash(event, true);
                closeEvent.fire();
                break;
            case 9: // tab key - if modal, keep focus inside the dialog
                if (isModal) {
                    if (currentFocus === closeButton && !shiftPressed) {
                        $A.util.squash(event, true);
                        firstFocusable.focus();
                    } else if (currentFocus === firstFocusable && shiftPressed) {
                        $A.util.squash(event, true);
                        closeButton.focus();
                    }
                // if not modal, close the dialog when you tab out of it
                } else {
                    if ((currentFocus === closeButton && !shiftPressed) ||
                        (currentFocus === firstFocusable && shiftPressed)) {
                        $A.util.squash(event, true);
                        closeEvent.fire();
                    }
                }
                break;
        }

    },


    /**
     * Constructs the handler for the DOM click event.
     * 
     * @param {Aura.Component} dialog the ui:dialog component
     * @param {Boolean} clickOutToClose whether the dialog should be closed on click outside the dialog
     * @param {UIEvent} event the DOM click event
     * @return {void}
     */
    getClickHandler : function(dialog, clickOutToClose, event) {

        if (!event) { event = window.event; }

        var target        = event.target || event.srcElement,
            container     = dialog.find("outer").getElement(),
            clickedInside = $A.util.contains(container, target),
            closeEvent;

        if (clickedInside) {
            return;
        } else {
            if (clickOutToClose) {
                closeEvent = $A.get("e.ui:closeDialog");
                closeEvent.setParams({
                    dialog : dialog,
                    confirmClicked : false
                });
                closeEvent.fire();
            }
        }

    },


    /**
     * Constructs the handler for the window.resize DOM event.
     * 
     * @param {Aura.Component} dialog the ui:dialog component
     * @param {Boolean} isModal whether the dialog is modal or not
     * @return {void}
     */
    getResizeHandler : function(dialog, isModal) {

        if (isModal) {
            dialog.find("content").getElement().style.maxHeight = this.getContentMaxHeight(dialog) + "px";
        }

    },


    /**
     * Retrieves the first focusable element inside the dialog component. Should
     * ALWAYS return a non-null value, as the "x" (i.e. dialog close) link should always
     * be visible and positioned as the very last element in the dialog window.
     * (Having a known element as the last item in the dialog makes keyboard management
     * much easier.)
     *
     * NOTE: This method uses querySelectorAll(), which IE7 doesn't like, so IE7 will
     * always focus on the "x" link, instead of the first element.
     * 
     * @param {Aura.Component} cmp the ui:dialog component
     * @return {HTMLElement} the first focusable element inside the dialog, or the "x" link for IE7
     */
    getFirstFocusableElement : function(dialog) {

        var container    = dialog.find("outer").getElement(),
            close        = dialog.find("closeButton").getElement(),
            formElements = [],
            length       = 0,
            element      = null;

        if (!container) {
            $A.assert(false, "Trying to find a focusable element in the dialog, but no container specified.");
        } else if (document.querySelectorAll) {
            // sorry IE7, you're outta luck
            formElements = container.querySelectorAll("input,button,a,textarea,select,[tabindex='0']");
            length = formElements.length;
            if (length > 0) {
                var hiddenPattern = /hidden/i;
                for (var i=0; i<length; i++) {
                    if (!formElements[i].disabled && !hiddenPattern.test(formElements[i].type)) {
                        element = formElements[i];
                        break;
                    }
                }
            } else {
                // we should never get here - at a minimum, the "close" link should always be present
                $A.assert(false, "No focusable element found.");
            }
        } else {
            element = close;
        }

        return element;

    },


    /**
     * Handles the application and removal of CSS classes that control the visibility of
     * all dialog types, as well as the animation behaviour of modal dialogs. This method
     * also handles focusing on the proper element when a dialog is opened or closed.
     * 
     * @param {Boolean} show specifies if the dialog should be shown (true) or hidden (false)
     * @param {Aura.Component} maskCmp the ui:dialog's "mask" component
     * @param {Aura.Component} dialog the ui:dialog component
     * @param {Boolean} autoFocus specifies if focus should automatically be applied to the first element in the dialog
     * @param {Boolean} isModal specifies if this dialog is modal
     * @param {Object} config JS object that contains references to the elements to focus
     * @return {void}
     */
    toggleDisplay : function(show, dialog, autoFocus, isModal, config) {

        var mask         = isModal ? dialog.find("mask").getElement() : null,
            outer        = dialog.find("outer").getElement(), // outer dialog wrapper
            inner        = dialog.find("content").getElement(), // inner content wrapper (i.e., the part that is scrollable)
            flickerDelay = 50,
            focusDelay   = 300,
            hideDelay    = 400;

        // if the dialog should be opened, remove the 'hidden' classes and apply the animation classes
        if (show) {
            $A.util.removeClass(outer, "hidden");
            if (isModal) {
                inner.style.maxHeight = this.getContentMaxHeight(dialog) + "px";
                $A.util.removeClass(mask, "hidden");
                // delay the application of animation classes by just a hair ... webkit/ffx rendering bug
                window.setTimeout(function() { $A.util.addClass(mask, "fadeIn"); }, flickerDelay);
                window.setTimeout(function() { $A.util.addClass(outer, "slideUp"); }, flickerDelay);
            }
            // apply proper element focus if necessary
            if ((autoFocus || isModal) && config.newFocus) {
                if (isModal) {
                    // delay focus until the modal slides into place, otherwise the scroll jumps
                    window.setTimeout(function() { config.newFocus.focus(); }, flickerDelay + focusDelay);
                } else {
                    config.newFocus.focus();
                }
            }
        // if the dialog should be closed, add the 'hidden' classes and remove the animation classes
        } else {
            if (isModal) {
                // remove the animation classes immediately, but delay adding 'hidden' back until animation completes
                $A.util.removeClass(mask, "fadeIn");
                $A.util.removeClass(outer, "slideUp");
                window.setTimeout(function() { $A.util.addClass(mask, "hidden"); }, hideDelay);
                window.setTimeout(function() { $A.util.addClass(outer, "hidden"); }, hideDelay);
            } else {
                // if not a modal, then just hide the dialog immediately
                $A.util.addClass(outer, "hidden");
            }
            
            // apply proper element focus if necessary
            if (config.oldFocus) {
            	try {
            		config.oldFocus.focus();
            	} catch (e) {
            		// Ignore these - invalid element or in IE7/8 trying to set focus to an invisible element
                    return;
            	}
            }
        }

    },


    /**
     * Calculates the max-height of the content <div> in a modal window so
     * it doesn't extend outside the viewport.
     * 
     * @return {void}
     */
    getContentMaxHeight : function(dialog) {

        var hasButtons     = dialog.get("v.buttons.length") > 0,
            cssMargin      = 10, // applied to top-level "title", "content", and "button" elements
            cssPadding     = 10, // applied to "content" <div> only
            titleHeight    = dialog.find("title").getElement().offsetHeight + cssMargin * 2, // account for top & bottom
            contentPadding = cssPadding * 2, // account for top & bottom
            buttonHeight   = hasButtons ? dialog.find("buttons").getElement().offsetHeight + cssMargin * 2 : cssMargin,
            extraMargin    = 20; // extra margin at the bottom of the viewport so you can fully see the css box-shadow effect

        // 2600px is the same value as the CSS 3D transform rule applied to the dialog
        return Math.min($A.util.getWindowSize().height, 2600) - (titleHeight + contentPadding + buttonHeight + extraMargin);

    },


    /**
     * Gets a root ui:dialog instance, even if the component to check is
     * an extension of ui:dialog.
     * 
     * @param {Aura.Component} cmp The component to check
     * @return {Aura.Component} cmp The root ui:dialog component
     */
    getDialogRoot : function(cmp) {
        var type = cmp.getDef().getDescriptor().getQualifiedName();
        while (type !== "markup://ui:dialog") {
            cmp = cmp.getSuper();
            type = cmp.getDef().getDescriptor().getQualifiedName();
        }
        return cmp;
    }

})// eslint-disable-line semi

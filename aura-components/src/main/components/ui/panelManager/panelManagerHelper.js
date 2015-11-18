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
	PANEL_TYPE : {DIALOG: "ui:panelDialog"},
    
	initialize: function(cmp) {
        // store management state on the component
        cmp._active = null; // current visible panel instance
        cmp._stack = []; // stack of instantiated panels
        cmp._actionQueue = []; // queue for storing open actions if a transition is already in progress
        cmp._deleteQueue = []; // queue for storing remove actions if transition is already in progress
        cmp._startStackLevel = 20; // z-index
        cmp._panels = {};
    },

    // helper; shallow copy
    _copy: function(obj1) {
        var obj2 = {};
        for (var p in obj1) {
            if (obj1.hasOwnProperty(p)) {
                obj2[p] = obj1[p];
            }
        }
        return obj2;
    },
    
    getManager: function(cmp) {
    	//use concrete component as manager to store all the states
    	return cmp.getConcreteComponent();
    },

    // for panelOverlay, create a 'Cancel' button to close the panel
    createCancelButton: function(cmp) {
        var button = $A.newCmp({
            componentDef: 'markup://ui:button',
            attributes: {
                values: {
                    label: cmp.get("v.cancelButtonLabel")
                }
            }
        });
        button.addHandler('press', cmp, 'c.closePanel');
        return button;
    },

    // ui:updatePanel handler 
    // updates the content of an existing panel
    updatePanel: function(cmp, event) {
        var self = cmp.getConcreteComponent().getDef().getHelper(),
            config = event.getParams() || {},
            panel = config.instance,
            headerActions = config.headerActions,
            body = config.body,
            maxButtons = 2,
            headerButtons,
            el;

        // is the panel still valid?
        if (!panel || !panel.isValid()) {
            return;
        }
        
        el = panel.getElement();
        
        // if the panel is transitioning, hold off on updating content
        // until the transition completes
        if (!panel.isRendered() || $A.util.hasClass(el, 'sliding')) {
            setTimeout(function() {
            	$A.run(function() {
            		self.updatePanel(cmp, event);
            	});
            }, 50);
            return;
        }

        if (!headerActions) {
        	headerActions = body && body.get('v.headerActions');
        }
        
        headerButtons = self.getHeaderButtons(cmp, headerActions, maxButtons);
        
        // do not force headerActions like we do in createPanelOverlay;
        // if headerActions not provided, that is ok
        
        // let the panel do the actual updating so it can manage any extra
        // DOM-specific logic (such as fade-in)
    	panel.get('c.update').run({ body: body, headerButtons: headerButtons });
    	var manager = this.getManager(cmp);
    	// if this is the active panel then set focus to the first focusable element
    	if (panel === manager._active && panel.get('v.autoFocus') !== false) {
    		setTimeout(function() {
    			$A.run(function() {
	    			if (panel.isValid()) {
	    				var focusables = self.getFocusables(panel);
	    				focusables.initial && focusables.initial.focus();
	    			}

                    if (config.onUpdate && $A.util.isFunction(config.onUpdate)) {
                        config.onUpdate(panel);
                    }
    			});
    		}, 100);
    	}
        
        // updatePanel event supports onCreate callback (passed through by showPanel in centerStage)
        if (config.onCreate && $A.util.isFunction(config.onCreate)) {
        	// allow new content to be rendered by aura before invoking callback
        	setTimeout(function() {
        		$A.run(function() {
        			config.onCreate(panel);
        		});
        	},10);
        }
    },
    
    getHeaderButtons: function(cmp, headerActions, maxButtons) {
    	var headerButtons = [];
    	if ($A.util.isArray(headerActions)) {
        	// note: this is essentially the same logic as that found in centerStage._preparePanelHeader().
        	// it ensures that at most two actions appear on the panel header and that the actions
        	// are proper components
        	headerActions = headerActions.slice(0, maxButtons);
        	for (var i=0, len = headerActions.length; i < len; i++) {
        		var button = headerActions[i];
        		if ($A.util.isComponent(button)) {
        			headerButtons[i] = button;
        		}
        	}
        }
    	return headerButtons;
    },

    // ui:openPanel handler 
    // creates panel if it does not already exist and then displays it
    openPanel: function(cmp, config) {
        var self = this,
            manager = this.getManager(cmp),
            panel = config.instance,
            transitioningType,
            transitioningIsModal;

        // This test is to guard against the user clicking fast on an element that triggers
        // a panel and causing two panels to be displayed.  However, we need allow legitimate cases of two panels being shown
        // at about the same time (the specific use case is when a force:showOfflineMessage
        // event is fired on the heels of a showPanel event, the offline modal must be displayed).
        // The check below is now looser to allow cases that we can be absolutely sure are not double-click
        // related, that of which the two panels are of different types.
        if (manager._transitioning === 'open') {
        	if (manager._transitioningInstance.isValid()) {
	        	transitioningType = manager._transitioningInstance.getDef().getDescriptor().getQualifiedName();
	        	transitioningIsModal = this.isModal(transitioningType); 
	        	if ((config.isModal && transitioningIsModal) || (!config.isModal && !transitioningIsModal)) {
	        		return;
	        	}
        	}
        	else {
        		return;
        	}
        }

        function callback(panelToDisplay) {
            // give aura a chance to inject the new component into the dom
            setTimeout(function() {
            	$A.run(function() {
            		// if we're also showing the panel, start the openInstance bit before invoking onCreate callback
            		// otherwise we get race conditions when onCreate callback code tries to hide the dialog
            		// before it's started the show process.
		            if (config.show) {
                		self.openInstance(cmp, panelToDisplay, config);
		            }
		            if (config.callbacks && $A.util.isFunction(config.callbacks.onCreate)) {
		                config.callbacks.onCreate(panelToDisplay);
		            }
            	});
            }, 0);
        }

        if (panel) {
            // if it has already been created, display it
            callback(panel);
        } else {
            this.createPanel(cmp, config, callback);
        }
    },
    
    createPanel: function(cmp, config, callback) {
        if (config.isModal || config.isDialog) {
            this.createPanelDialog(cmp, config, callback);
        }
    },

    // ui:createModal handler; creates PanelDialog cmp and inserts into dom
    createPanelDialog: function(cmp, config, callback) {
        var actionList = config.body;

        // don't modify original config object; needed in testing code
        config = this._copy(config);
        this.beforeCreatePanel(cmp, config);
        config.animation = config.animation || 'bottom';
        config.closeAction = this.getCloseActionForModal(cmp);
		
		 // delay setting panel content until panel has been inserted into the dom (bad things happen otherwise)
		 config.body = [];
		
		 // return the promise
		 this._createPanel(cmp, this.PANEL_TYPE.DIALOG, config, function(panel) {
			 panel.set('v.body', actionList);
		     panel._isModal = config.isModal;
		     callback && callback(panel);
		 });
    },
    
    //Hook for subcomponent
    beforeCreatePanel: function() {
    },
    
    isModal: function(panelType) {
    	return  panelType === 'markup://ui:panelDialog';
    },

    getCloseActionForModal: function() {
    	return function() {
   		     // On close, fire event that can be handled by many observers.
   			 $A.get('e.ui:closePanel').fire();
    	};
    },

    // generic create method for all panel types
    _createPanel: function(cmp, def, config, callback) {
    	var self = this;
        var referenceElement = config.referenceElement;
        config.referenceElement = null;

        // if no class set explicitly on the panel, use the class set on the panelManager
        config['class'] = config['class'] || cmp.get('v.class');
        
        this._createComponent(def, config, function(panel) {
            // add handler to trap the render event in order to invoke onCreate callback
            panel.addHandler('panelDoneRendering', cmp, 'c.onPanelLoaded');

            // store panel metadata in _panels hash so we can access it in our event handlers
            cmp._panels[panel.getGlobalId()] = {
                panel: panel,
                onCreate: config.onCreate || config.callback // @todo: add onCreate support to the events
            };

            // inject into dom
            self.attachPanelInstance(cmp, panel);

            panel.set("v.referenceElement", referenceElement);

            callback && callback(panel);
        });
    },

    // generic async method to create a component 
    _createComponent: function(def, attr, callback) {
        $A.newCmpAsync(null, callback, {
            componentDef: def,
            attributes: {
                values: attr
            }
        });
    },

    // insert a new panel into the dom
    attachPanelInstance: function(cmp, panel) {
        var container = this._findContainer(cmp, 'container'),
            body = container.get('v.body');

        body.push(panel);
        container.set("v.body", body);
    },
    
    _findContainer: function(cmp, id) {
    	var p = cmp;
    	var container = cmp.find(id);
    	while (!container && p.isInstanceOf("ui:panelManager")) {
    		p = p.getSuper();
    		container = p.find(id);
    	}
    	return container;
    },

    // transition a panel into the visible state
    openInstance: function(cmp, panel, config) {
        var self = this, manager = this.getManager(cmp);

        if (!manager._transitioning) {
            if(panel.isRendered()) {
                // avoid more actions at the same time
                // If DOM is already there open the panel
                panel.get('c.show').run();
             } else {
                 // Otherwise wait until panel is done rendering and then show it
                panel.addHandler('panelDoneRendering', panel, 'c.show'); 
             }
        } else {
            // queue the action to execute it once the transition is done.
        	manager._actionQueue.push(function() {
                self.openInstance.call(self, cmp, panel, config);
            });
        }
    },
    
    isPanel: function(component) {
    	var isPanel = component && 
    		$A.util.isComponent(component) &&
    		/(ui\:panelOverlay|ui\:panelDialog)/.test(component.getDef().getDescriptor().getQualifiedName());
    	return isPanel;
    },

    // transition a panel into the hidden state;  
    closeInstance: function(cmp, panel, options) {
        var manager = this.getManager(cmp),
        	self = this,
            slider,
            stack = manager._stack,
            containerEl = manager.getElement();
        if (panel && !panel.isValid()) {
        	//Do nothing if panel is not valid 
        	return;
        }
        
        if (manager._transitioning) {
        	// don't lose the closeInstance request, queue it
        	manager._actionQueue.push(function() {
                self.closeInstance.call(self, cmp, panel, options);
            });
            return;
        }
            
        // guard against non-panels being set as instance param in hidePanel event
        if (!this.isPanel(panel)) {
        	panel = null;
        }
            
        panel = panel || manager._active;
        
        if (!panel) {
            // special bit to handle "visible but not active" panelSlider
            if (stack && stack.length) {
                slider = stack[stack.length-1];
                slider.get('c.hide').run();
            }
            return;
        }

        options || (options = {
            lazyDestroy: true
        });
        
        containerEl.style.overflow = 'hidden';
        panel.get('c.hide').run(options);
    },

    // remove any accumulated delete actions that are pending
    emptyDeleteQueue: function(cmp) {
        var manager = this.getManager(cmp),
            toDelete = manager._deleteQueue;

        var iterator;
        while ((iterator = toDelete.shift())) {
            iterator();
        }
    },

    /*
     * RemoveInstance conditions: If there is an action on the queue, we need to
     * wait to destroy the instance If there is any previous panel on the queue
     * to be removed we will remove it first and then we will place this one.
     * The reason for getting always one in the queue is because may have
     * intrinsic dependencies between both panels Example: UIServices
     * Confirmation panel holds references to the first panel, so we cannot
     * remove it until this is closed.
     */
    removeInstance: function(cmp, panel, forceImmediateDestroy) {
        var self = this,
            manager = this.getManager(cmp),
            container = this._findContainer(cmp, 'container'),
            stackedItems = container.get('v.body').length;

        // per Diego; keep one modal in the dom
        if (!panel._isModal || forceImmediateDestroy) {
            this.emptyDeleteQueue(cmp);
            this._removePanel(cmp, panel);
            return;
        }

        // if there is something in the queue wait til next one closes
        if (manager._actionQueue.length || stackedItems === 0) {
            manager._deleteQueue.push(function() {
                self._removePanel.call(self, cmp, panel);
            });
        } else {
            this.emptyDeleteQueue(cmp);
            manager._deleteQueue.push(function() {
                self._removePanel.call(self, cmp, panel);
            });
        }
    },

    // remove a single panel from the dom and invoke its destroy method
    _removePanel: function(cmp, panel) {
        var i, pos = -1,
            manager = this.getManager(cmp),
            panelId = panel.getGlobalId(),
            container = this._findContainer(cmp, 'container'),
            auraContainer = container.get('v.body'),
            stackedItems = auraContainer.length;

        for (i = 0; i < stackedItems; i++) {
            if (auraContainer[i] === panel) {
                pos = i;
                break;
            }
        }

        if (pos !== -1) {
            auraContainer.splice(pos, 1);
            container.set("v.body", auraContainer);
        }

        manager._panels[panelId] = null;
    },

    // one:destroySlidePanel handler (coming soon)
    destroySlidePanelDEPRECATED: function(cmp, event) {
        this.destroyPanel(cmp, event);
    },

    // force:destroyPanel handler
    destroyPanel: function(cmp, event) {
        var params = event.getParams() || {},
        	manager = this.getManager(cmp),
            panelId, config, panel;

        // handling both new API and previous destroySliderPanel API for now
        if (params.instance) {
            panel = params.instance;
            panelId = panel.getGlobalId();
        } else if (params.panel) {
            if (typeof params.panel === 'string') {
                panelId = params.panel;
            } else {
                panelId = params.panel.getGlobalId();
            }
        }

        config = manager._panels[panelId];
        panel = panel || (config && config.panel);
        // if (!panel) {
            // @todo: invalid state
        // }

        // @todo: revisit this sequence when destroy events are added
        this.closeInstance(cmp, panel, {removeAnim: true});
        this.unstackPanel(cmp, panel);

        panel && this.removeInstance(cmp, panel, true);

        if (params.callback) {
            params.callback.call(null, panelId);
        }
        if (params.onDestroy) {
            params.onDestroy.call(null, panelId);
        }
    },

    // remove all panels from the dom
    destroyAllPanels: function(cmp) {
        var self = this,
            container = this._findContainer(cmp, 'container'),
            body = container.get('v.body');

        for (var i = 0; i < body.length; i++) {
            self.closeInstance(cmp, body[i], {
                lazyDestroy: true,
                removeAnim: true
            });
        }

        this.emptyDeleteQueue(cmp);
        container.set("v.body", []);
    },

    // return the currently visible panel
    getActiveInstance: function(manager) {
        return manager._active;
    },

    // set the currently visible panel, update classes and aria-hidden attr on all panels
    setActiveInstance: function(cmp, panel) {
        var manager = this.getManager(cmp),
            panels = document.querySelectorAll('.dialog-wrapper > *');

        manager._active = panel;

        // set all panels inactive
        for (var i = 0; i < panels.length; i++) {
            $A.util.removeClass(panels[i], 'active');
            panels[i].setAttribute('aria-hidden', 'true');
        }
        var helper = cmp.getConcreteComponent().getDef().getHelper();
        helper.afterSetActiveInstance(panel);

        if (panel && panel.isValid()) {
            var panelDom=panel.getElement();
            if(panelDom) {
                $A.util.addClass(panelDom, 'active');
                panelDom.setAttribute('aria-hidden', 'false');
            }
            this.bindKeyHandler(manager);
            //TODO: need to decouple the logic here
            if (panel.get("v.isModal") === false && panel.get("v.closeOnClickOut") && panel.getDef().getDescriptor().getQualifiedName().indexOf("panelSlider") === -1) {
                this.bindClickHandler(manager);
            }
        } else {
            this.unbindKeyHandler(manager);
            this.unbindClickHandler(manager);
        }
    },
    
    afterSetActiveInstance: function() {
    	//template for  subcomponents
    },
    
    /**
     * TODO: Need to find a better way of updating the panel zIndex
     */
    _updateModalGlasszIndex: function(panelHelper, panel, stackIndex) {
    	if (panelHelper && panelHelper["getModalGlassElement"]) {
    		var el = panelHelper["getModalGlassElement"](panel);
    		if (el) {
  	            el.style.zIndex = stackIndex;
    		}
    	}
    },
    /**
     * TODO: Need to find a better way of updating the panel zIndex
     */
    _updatePanelElementzIndex: function(panelHelper, panel, stackIndex) {
    	if (panelHelper && panelHelper["getPanelElement"]) {
    		var el = panelHelper["getPanelElement"](panel);
    		if (el) {
    			el.style.zIndex = stackIndex;
    		}
    	}
    },

    // set the panel's zIndex stacking order; 
    // if the panel has a modal-glass component, update it too
    stackPanel: function(cmp, panel) {
		var panelHelper = panel.getDef().getHelper(),
            manager = this.getManager(cmp),
            stack = manager._stack,
            stackIndex = manager._startStackLevel + stack.length + 1;
        //only stack if it's new panel
        if (stack[stack.length - 1] === panel) {
            this.setActiveInstance(cmp, panel);
        } else {
            stack.push(panel);
            this.setActiveInstance(cmp, panel);
            this._updateModalGlasszIndex(panelHelper, panel, stackIndex);
            this._updatePanelElementzIndex(panelHelper, panel, stackIndex);
        }
        
    },
    
    // remove the currently active panel from the top of the zIndex stack
    // and move the next panel on the stack to the active state
    unstackPanel: function(cmp, panel) {
        var manager = this.getManager(cmp),
            stack = manager._stack;
        // remove the current panel if and only if last on stack
        if (stack.indexOf(panel) === stack.length-1) {
            stack.pop();
            this.setActiveInstance(cmp, stack[stack.length-1]);
        }
    },

    isVisible: function(el) {
        while (el && el.style) {
            if (window.getComputedStyle(el).display === 'none') {
                return false;
            }
            el = el.parentNode;
        }
        return true;
    },

    focusAllowed: function(el) {
    	return el && !el.disabled && !/hidden/i.test(el.type) && this.isVisible(el);
    },

    // returns the initial, first and last focusable in the given panel
    getFocusables: function(panel) {
        var panelEl = panel.getElement(),
            els = panelEl.querySelectorAll('input,button,a,textarea,select'),
            len = els.length,
            i, el;

        // The 'initial' element is the first non-button focusable element (see W-2512261)
        // whereas the 'first' element is the first (button or non-button) focusable element
        var initial, first, last;

        for (i = 0; i < len; i++) {
            el = els[i];
            if (this.focusAllowed(el)) {
                if (!first) {
                    first = el;
                }
                if (!/button/i.test(el.type)) {
                    initial = el;
                    break;
                }
            }
        }

        for (i = len - 1; i >= 0; i--) {
            el = els[i];
            if (this.focusAllowed(el)) {
                last = el;
                break;
            }
        }

        return {
            // security restriction on iOS doesn't allow focus without user gesture, and focusing an input
            // inside a timeout causes weird behaviours (See W-2564192)
            // we can't completely ignore focus in iOS because the header is positioned on focus. Therefore
            // allowing iOS to focus the header buttons as before.
            initial: initial && !$A.get('$Browser.isIOS') ? initial : first,
            first: first,
            last: last
        };
    },

    // watches for <tab> key to control focus; panels handle ESC key as needed
    bindKeyHandler: function(manager) {
        var self = this;

        if (manager._keydownHandler) {
            return;
        }

        manager._keydownHandler = function(e) {
            var event = e || window.event,
                shiftPressed = event.shiftKey,
                current = document.activeElement,
                active = manager._active,
                isModal = active.get("v.isModal") !== false,
                focusables;

            if (active && event.keyCode === 9) {
                focusables = self.getFocusables(active);
                if (isModal) {
                    if (current === focusables.last && !shiftPressed) {
                        $A.util.squash(event, true);
                        focusables.first.focus();
                    } else if (current === focusables.first && shiftPressed) {
                        $A.util.squash(event, true);
                        focusables.last.focus();
                    }
                } else {
                    if (current === focusables.last && !shiftPressed) {
                        $A.util.squash(event, true);
                        $A.get('e.ui:closePanel').fire();
                    }
                }
            }
        };

        $A.util.on(document, 'keydown', manager._keydownHandler, false);
    },

    unbindKeyHandler: function(manager) {
        if (!manager._keydownHandler) {
            return;
        }
        $A.util.removeOn(document, 'keydown', manager._keydownHandler, false);
        manager._keydownHandler = null;
    },

    bindClickHandler: function(manager) {

        if (manager._clickHandler) {
            return;
        }

        manager._clickHandler = function(e) {
            var event = e || window.event,
                active = manager._active,
                panel  = active.find("panel").getElement(),
                target = event.target || event.srcElement,
                clickedInside = $A.util.contains(panel, target);
            if (active && !clickedInside) {
                $A.get('e.ui:closePanel').setParams({instance: active}).fire();
            }
        };

        $A.util.on(document, 'click', manager._clickHandler, false);
    },

    unbindClickHandler: function(manager) {
        if (!manager._clickHandler) {
            return;
        }
        $A.util.removeOn(document, 'click', manager._clickHandler, false);
        manager._clickHandler = null;
    },

    transitionBegin: function(cmp, event) {
        var params = event.getParams(),
            container = cmp.getElement(),
            manager = this.getManager(cmp),
            active = manager._active;

        if (params.isOpening) {
        	// if there is already an active panel, keep track of the scroll position
        	// so we can reset it when this new panel is closed; the body scroll
        	// position gets hosed when we set our container overflow to hidden below
        	// but we need to do that for proper animation display
            if (active) {
            	active._scrolled = document.body.scrollTop;
            }
            manager._transitioning = 'open';
            manager._transitioningInstance = params.panel;
            container.style.overflow = 'hidden';
            this.stackPanel(cmp, params.panel);
        } else {
        	manager._transitioning = 'close';
        	manager._transitioningInstance = null;
            container.style.overflow = '';
        }
    },

    // ui:panelTransitionEnd handler
    // if 'hide' transition has ended then remove transient panel from the dom
    transitionEnd: function(cmp, event) {
        var manager = this.getManager(cmp),
            container = cmp.getElement(),
            actionDone = event.getParam('action'),
            panelId = event.getParam('panelId'),
            isTransient = event.getParam('isTransient'),
            currentInstance = manager._transitioningInstance || $A.getCmp(panelId);

        manager._transitioning = null;
        container.style.overflow = '';

        if (actionDone === 'show' && currentInstance && currentInstance.isValid()) {
        	this.setFocus(cmp, currentInstance);
        } else if (actionDone === 'hide') {
            // transient panels get nuked
            if (isTransient) {
                this.removeInstance(cmp, currentInstance);
            }
            this.unstackPanel(cmp, currentInstance);
        }

        manager._transitioningInstance = null;
        function executeAction(action) {
        	$A.run(function() {
        		action();
        	});
        }
        //dequeue all the actions that got queued up while the panel is opening or closing
        while (manager._actionQueue.length > 0) {
            setTimeout(executeAction.bind(this, manager._actionQueue.shift()), 0);
        }
    },
    /**
     * Set given panel active
     */
    setFocus: function(cmp, panel) {
    	var me = this, panelDom = panel.getElement();
        if (panelDom) {
        	// if we have previously stored the body scroll position for this panel, now is the time to reset it
            if (panel._scrolled)  {
            	document.body.scrollTop = panel._scrolled;
            }
            
            if (panel.get('v.autoFocus') !== false) {
                // if panel is being reshown because a stacked panel was hidden, reset the previously
                // focused element, otherwise set focus to the first focusable element
                var last = panel.get('v.lastFocusedInput');
                if (last && !last.disabled && me.isVisible(last)) {
                	last.focus();
                }
                else {
	                setTimeout(function() {
	                	$A.run(function() {
	                		var focusables;
		                    if (!panel.isValid()) {
		                        return;
		                    }
		                    focusables = me.getFocusables(panel);
		                    focusables.initial && focusables.initial.focus();
	                	});
	                }, 0);
            	}
            }
        }
    },

    // ui:panelDoneRendering handler
    // call onCreate callback if it exists
    onPanelLoaded: function(cmp, event) {
        var params = event.getParams(),
            config = cmp._panels[params.panelId],
            onCreate = config && config.onCreate,
            panel = config && config.panel;

        if (onCreate && panel) {
            if (typeof onCreate === 'function') {
                onCreate.call(null, panel);
            } else {
                onCreate.run(panel);
            }
        }
    }
})// eslint-disable-line semi

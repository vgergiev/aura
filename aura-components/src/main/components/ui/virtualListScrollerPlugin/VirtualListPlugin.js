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
function lib(w) { //eslint-disable-line no-unused-vars
    'use strict';
    w || (w = window);

    if (w.Scroller) {
        var VirtualListPLugin = {
            init: function () {
                this.on('_initialize', this._initializeVirtualPlugin);
            },
            _initializeVirtualPlugin: function () {
                console.log('>> initializing VirtualListPlugin'); //eslint-disable-line no-console
            }
        };

        w.Scroller.registerPlugin('VirtualList', VirtualListPLugin);
        return VirtualListPLugin;
    } else {
        $A.warning('Error trying to register VirtualListPlugin: No Scroller var defined');
    }
}

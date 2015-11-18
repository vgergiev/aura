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
Function.RegisterNamespace("Test.Aura.Component");

[Fixture]
Test.Aura.Component.ComponentDefStorageTest = function () {

    var Aura = { Component: {} };

    Mocks.GetMocks(Object.Global(), {
        Aura: Aura,
        "ComponentDefStorage": function(){} // Prevent Global Reference
    })
    (function () {
        [Import("aura-impl/src/main/resources/aura/component/ComponentDefStorage.js")]
    });

    var mockAuraUtil = Mocks.GetMocks(Object.Global(),{
        "$A": {
            util: {
                isString: function () {
                    return true;
                },
                isUndefinedOrNull: function (obj) {
                    return obj === undefined || obj === null;
                }
            },
            warning: function (message, error) {},
            assert: function (condition, message) {
                if (!condition) {
                    throw Error(message);
                }
            },
            Perf: {
                mark: function () {
                },
                endMark: function () {
                }
            }
        },
        Aura: Aura
    });



    [Fixture]
    function ShouldStore() {

        [Fact]
        function ShouldNotStoreNonLayoutDef() {
            var target = new Aura.Component.ComponentDefStorage();
            Assert.False(target.shouldStore("markup://foo:bar"));
        }

        [Fact]
        function ShouldStoreLayoutDef() {
            var target = new Aura.Component.ComponentDefStorage();
            Assert.True(target.shouldStore("layout://foo:bar"));
        }
    }

    [Fixture]
    function SetupDefinitionStorage() {

        var mockStorageService = function (persistent, secure) {
            return Mocks.GetMock(Object.Global(), "$A", {
                storageService: {
                    initStorage: function() {
                        return {
                            isPersistent: function() {
                                return persistent;
                            },
                            isSecure: function() {
                                return secure
                            }
                        }
                    },
                    deleteStorage: function() {}
                },
                getContext: function() {
                    return {
                        getApp: function() {
                            return "foo";
                        }
                    }
                }
            });
        };

        [Fact]
        function ShouldNotUseNotPersistentStorage() {
            var target = new Aura.Component.ComponentDefStorage();
            target.useDefStore = undefined;
            mockStorageService(false, false)(function () {
                target.setupDefinitionStorage();
            });

            Assert.False(target.useDefStore);
        }

        [Fact]
        function OnlyUsePersistent() {
            var target = new Aura.Component.ComponentDefStorage();
            target.useDefStore = undefined;
            mockStorageService(true, false)(function () {
                target.setupDefinitionStorage();
            });

            Assert.True(target.useDefStore);
        }

    }

};

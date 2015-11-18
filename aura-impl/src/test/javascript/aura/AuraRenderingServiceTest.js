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
Function.RegisterNamespace("Test.Aura");

[Fixture]
Test.Aura.AuraRenderingServiceTest = function(){
    var context={getCurrentAccess:function(){},setCurrentAccess:function(){},releaseCurrentAccess:function(){}};
    var $A = {
        getContext:function(){return context;},
        ns : {}
    };
    var Aura = {Services: {}};

    //Mock the exp() function defined in Aura.js, this is originally used for exposing members using a export.js file
    Mocks.GetMocks(Object.Global(), {
        "Aura": Aura,
        "AuraRenderingService":function(){}
    })(function(){
        [Import("aura-impl/src/main/resources/aura/AuraRenderingService.js")]
    });

    // Mocks necessary to create a new AuraComponentService Object
    var mockOnLoadUtil = Mocks.GetMocks(Object.Global(), {
        "ComponentDefRegistry": function(){},
        "ControllerDefRegistry": function(){},
        "ActionDefRegistry": function(){},
        "ModelDefRegistry": function(){},
        "$A": $A
    });

    var getFakeComponent = function(valid, rendered, name) {
        var rendererDef = {
            rerender : Stubs.GetMethod("component", null),
            unrender : Stubs.GetMethod("component", null),
            afterRender : Stubs.GetMethod("component", null)
        };
        return {
            getGlobalId : function() { return "1"; },
            getElements : function() { return [ "1" ]; },
            isValid : function() { return valid; },
            getDef : function() {
                return {
                    getDescriptor : function() { return name; },
                };
            },
            render : Stubs.GetMethod("xxx", "yyy", null),
            rerender : Stubs.GetMethod("component", null),
            unrender : Stubs.GetMethod("component", null),
            afterRender : Stubs.GetMethod("component", null),
            isRendered : function() { return rendered; },
            setRendered : Stubs.GetMethod('value', null),
            getRenderer : function () {
                return {
                    def : rendererDef,
                    renderable : name
                };
            }
        };
    };

    var forceStub = function(fakeComp, name, fn) {
        if (name == 'render') {
            fakeComp.render = fn;
        } else {
            fakeComp.getRenderer().def[name] = fn;
        }
    };

    var getStubs = function(fakeComp) {
        var stubs = {};
        var renderer = fakeComp.getRenderer().def;
        stubs.rerender = fakeComp.rerender;
        stubs.unrender = renderer.unrender;
        stubs.afterRender = fakeComp.afterRender;
        stubs.render = fakeComp.render;
        return stubs;
    };

    var getMockAuraInfo = function(shouldBeComponent) {
        return {
            "mock":Mocks.GetMock(Object.Global(), "$A", {
                getContext:function(){return context;},
                util: {
                    isComponent : function() { return shouldBeComponent; },
                    isArray : function(obj) { return obj instanceof Array; }
                },
                assert: function(condition, message) {
                    if (!condition) {
                        throw new Error(message);
                    }
                }
            })
        };
    };


    [Fixture]
    function Rerender(){
        [Fact]
        function AssertRerenderCalled() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var mockAuraInfo = getMockAuraInfo(true);
            var expectedRenderable = 'value';
            var mockComponent = getFakeComponent(true, true, expectedRenderable);

            // Act
            mockAuraInfo.mock(function() {
                target.rerender(mockComponent);
            });
            var stubs = getStubs(mockComponent);

            Assert.Equal(1, stubs.rerender.Calls.length);
        }

        [Fact]
        function AssertRenderNotCalled() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var mockAuraInfo = getMockAuraInfo(true);
            var expectedRenderable = 'value';
            var mockComponent = getFakeComponent(true, true, expectedRenderable);

            // Act
            mockAuraInfo.mock(function() {
                target.rerender(mockComponent);
            });
            var stubs = getStubs(mockComponent);
            Assert.Equal(0, stubs.render.Calls.length);
        }

        [Fact]
        function AssertUnRenderNotCalled() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var mockAuraInfo = getMockAuraInfo(true);
            var expectedRenderable = 'value';
            var mockComponent = getFakeComponent(true, true, expectedRenderable);
            // Act
            mockAuraInfo.mock(function() {
                target.rerender(mockComponent);
            });
            var stubs = getStubs(mockComponent);
            Assert.Equal(0, stubs.unrender.Calls.length);
        }

        [Fact]
        function AssertAfterRenderNotCalled() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var mockAuraInfo = getMockAuraInfo(true);
            var expectedRenderable = 'value';
            var mockComponent = getFakeComponent(true, true, expectedRenderable);
            // Act
            mockAuraInfo.mock(function() {
                target.rerender(mockComponent);
            });
            var stubs = getStubs(mockComponent);
            Assert.Equal(0, stubs.afterRender.Calls.length);
        }
    }

    [Fixture]
    function AfterRender(){
        [Fact]
        function ErrorOnNoComponent() {
            var expected = "AuraRenderingService.afterRender: 'cmp' must be a valid Component, found 'bad'.";
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var mockAuraInfo = getMockAuraInfo(false);
            var actual;

            // Act
            try {
                mockAuraInfo.mock(function() {
                    target.afterRender("bad");
                });
            } catch (e) {
                actual = e.message;
            }


            Assert.Equal(expected, actual);

        }

        [Fact]
        function AssertAfterRenderNotCalledForInvalid() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var stubbedAfterRender = Stubs.GetMethod("rendered", null);
            var mockAuraInfo = getMockAuraInfo(true);
            var mockComponent = getFakeComponent(false, undefined, undefined)

            // Act
            mockAuraInfo.mock(function() {
                target.afterRender(mockComponent);
            });
            var stubs = getStubs(mockComponent);

            Assert.Equal(stubs.afterRender.Calls.length, 0);
        }

        [Fact]
        function AssertRenderNotCalledForInvalid() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var stubbedAfterRender = Stubs.GetMethod("rendered", null);
            var mockAuraInfo = getMockAuraInfo(true);
            var mockComponent = getFakeComponent(false, undefined, undefined)

            // Act
            mockAuraInfo.mock(function() {
                target.afterRender(mockComponent);
            });
            var stubs = getStubs(mockComponent);
            Assert.Equal(stubs.render.Calls.length, 0);
        }

        [Fact]
        function AssertReRenderNotCalledForInvalid() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var stubbedAfterRender = Stubs.GetMethod("rendered", null);
            var mockAuraInfo = getMockAuraInfo(true);
            var mockComponent = getFakeComponent(false, undefined, undefined)

            // Act
            mockAuraInfo.mock(function() {
                target.afterRender(mockComponent);
            });
            var stubs = getStubs(mockComponent);
            Assert.Equal(stubs.rerender.Calls.length, 0);
        }

        [Fact]
        function AssertUnRenderNotCalledForInvalid() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var stubbedAfterRender = Stubs.GetMethod("rendered", null);
            var mockAuraInfo = getMockAuraInfo(true);
            var mockComponent = getFakeComponent(false, undefined, undefined)

            // Act
            mockAuraInfo.mock(function() {
                target.afterRender(mockComponent);
            });
            var stubs = getStubs(mockComponent);
            Assert.Equal(stubs.unrender.Calls.length, 0);
        }

        [Fact]
        function ErrorOnAfterRenderDoesNotCallRender() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var expected = new Error("expected");
            var mockAuraInfo = getMockAuraInfo(true);
            var mockComponent = getFakeComponent(true, true, 'rendered');
            forceStub(mockComponent, 'afterRender', function () { throw expected; });
            // Act
            mockAuraInfo.mock(function() {
                target.afterRender(mockComponent);
            });

            var stubs = getStubs(mockComponent);
            //AfterRender is not a stub, it is replaced above.
            //Assert.Equal(stubs.afterRender.Calls.length, 1);
            Assert.Equal(0, stubs.render.Calls.length);
        }

        [Fact]
        function ErrorOnAfterRenderDoesNotCallReRender() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var expected = new Error("expected");
            var mockAuraInfo = getMockAuraInfo(true);
            var mockComponent = getFakeComponent(true, true, 'rendered');
            forceStub(mockComponent, 'afterRender', function () { throw expected; });
            // Act
            mockAuraInfo.mock(function() {
                target.afterRender(mockComponent);
            });

            var stubs = getStubs(mockComponent);
            //AfterRender is not a stub, it is replaced above.
            //Assert.Equal(stubs.afterRender.Calls.length, 1);
            Assert.Equal(0, stubs.rerender.Calls.length);
        }

        [Fact]
        function ErrorOnAfterRenderDoesNotCallUnRender() {
            var target;
            mockOnLoadUtil(function(){
                target = new Aura.Services.AuraRenderingService();
            });
            var expected = new Error("expected");
            var mockAuraInfo = getMockAuraInfo(true);
            var mockComponent = getFakeComponent(true, true, 'rendered');
            forceStub(mockComponent, 'afterRender', function () { throw expected; });
            // Act
            mockAuraInfo.mock(function() {
                target.afterRender(mockComponent);
            });

            var stubs = getStubs(mockComponent);
            Assert.Equal(0, stubs.unrender.Calls.length);
        }
    }
}

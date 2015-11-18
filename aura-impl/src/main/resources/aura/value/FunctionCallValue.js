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
/*jslint sub: true */
/**
 * @description A Value wrapper for a function call.
 * @constructor
 * @protected
 * @export
 */
function FunctionCallValue(config, valueProvider){
    this.valueProvider = valueProvider;
    this.byValue = config["byValue"];
    this.code = $A.util.json.decodeString(config["code"]);
    this.context = $A.getContext().getCurrentAccess();

    this.args = [];
    for (var i = 0; i < config["args"].length; i++) {
        this.args.push(valueFactory.create(config["args"][i], null, this.valueProvider));
    }

//#if {"modes" : ["STATS"]}
    valueFactory.index(this);
//#end
}

/**
 * Create a local subclass.
 */
FunctionCallValue.prototype.expressionFunctions = new ExpressionFunctions();

/**
 * Sets the isDirty flag to false.
 * @export
 */
FunctionCallValue.prototype.isDirty = function(){
	for (var i = 0; i < this.args.length; i++) {
        var arg = this.args[i];
        if (aura.util.isExpression(arg) && arg.isDirty()) {
            return true;
        }
    }
    return false;
};

/**
 * Returns the value of function call with the given value provider.
 * Throws an error if vp is not provided.
 * @param {Object} valueProvider The value provider to resolve.
 */
FunctionCallValue.prototype.evaluate = function(valueProvider){
    $A.getContext().setCurrentAccess(this.context);
    var result = this.code.call(null, valueProvider || this.valueProvider, this.expressionFunctions);
    if(!this.hasOwnProperty("result")){
        this["result"]=result;
    }
    $A.getContext().releaseCurrentAccess();
    return result;
};

/**
 * @export
 */
FunctionCallValue.prototype.addChangeHandler=function(cmp, key, fcv) {
    if(this.byValue){
        return;
    }
    if(!fcv){
        fcv=this;
    }
    for (var i = 0; i < this.args.length; i++) {
        var arg = this.args[i];
        if (aura.util.isExpression(arg)) {
            if(arg instanceof PropertyReferenceValue) {
                arg.addChangeHandler(cmp, key, this.getChangeHandler(cmp, key, fcv));
            } else {
                arg.addChangeHandler(cmp, key, fcv);
            }
        }
    }
};

FunctionCallValue.prototype.getChangeHandler=function(cmp, key, fcv) {
    return function FunctionCallValue$getChangeHandler(event) {
        var result = fcv.evaluate();
        if (fcv["result"] !== result) {
          fcv["result"] = result;
          $A.renderingService.addDirtyValue(key, cmp);
          cmp.fireChangeEvent(key, event.getParam("oldValue"), event.getParam("value"), event.getParam("index"));
        }
    };
};

/**
 * @export
 */
FunctionCallValue.prototype.removeChangeHandler=function(cmp, key){
    if(this.byValue){
        return;
    }
    for (var i = 0; i < this.args.length; i++) {
        var arg = this.args[i];
        if (aura.util.isExpression(arg)) {
            arg.removeChangeHandler(cmp, key);
        }
    }
};

/**
 * Destroys the value wrapper.
 * @export
 */
FunctionCallValue.prototype.destroy = function(){
//#if {"modes" : ["STATS"]}
    valueFactory.deIndex(this);
//#end
// JBUCH: HALO: TODO: FIXME
//    for(var i=0;i<this.args.length;i++){
//        this.args[i].destroy();
//    }
    this.args=this.code=this.valueProvider=null;
};

/**
 * Returns the JS function.
 * Helpful for logging/debugging.
 * @returns {String} FunctionCallValue
 * @export
 */
FunctionCallValue.prototype.toString = function(){
    return this.code.toString();
};

Aura.Value.FunctionCallValue = FunctionCallValue;


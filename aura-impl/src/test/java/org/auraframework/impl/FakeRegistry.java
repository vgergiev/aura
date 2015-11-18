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
package org.auraframework.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.auraframework.def.ClientLibraryDef;
import org.auraframework.def.DefDescriptor;
import org.auraframework.def.Definition;
import org.auraframework.def.DescriptorFilter;
import org.auraframework.system.MasterDefRegistry;
import org.auraframework.system.Source;
import org.auraframework.throwable.ClientOutOfSyncException;
import org.auraframework.throwable.quickfix.QuickFixException;

import com.google.common.collect.Maps;

/**
 * This is a fake registry (both master and standard) for testing purposes only.
 * Primarily used for testing validateReferences calls
 */
public class FakeRegistry implements MasterDefRegistry {
    private final Map<DefDescriptor<?>, Definition> stuff = Maps.newHashMap();

    public Definition putDefinition(Definition def) {
        return stuff.put(def.getDescriptor(), def);
    }

    public Definition removeDefinition(DefDescriptor<Definition> descriptor) {
        return stuff.remove(descriptor);
    }

    @Override
    public Set<DefDescriptor<?>> find(DescriptorFilter matcher) {
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D extends Definition> D getDef(DefDescriptor<D> descriptor) {
        return (D) stuff.get(descriptor);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <D extends Definition> D getRawDef(DefDescriptor<D> descriptor) {
        return (D) stuff.get(descriptor);
    }

    @Override
    public <D extends Definition> boolean exists(DefDescriptor<D> descriptor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <D extends Definition> void addLocalDef(D def) {
    }

    @Override
    public <T extends Definition> Source<T> getSource(DefDescriptor<T> descriptor) {
        return null;
    }

    @Override
    public boolean namespaceExists(String ns) {
        return false;
    }

    @Override
    public <D extends Definition> void assertAccess(DefDescriptor<?> referencingDescriptor, D def) throws QuickFixException {
    }
    
    @Override
    public Map<DefDescriptor<?>, Definition> filterRegistry(Set<DefDescriptor<?>> preloads) {
        return null;
    }

    @Override
    public <T extends Definition> String getUid(String uid, DefDescriptor<T> descriptor)
            throws ClientOutOfSyncException, QuickFixException {
        return null;
    }

    @Override
    public Set<DefDescriptor<?>> getDependencies(String uid) {
        return null;
    }

	@Override
    public String getCachedString(String uid, DefDescriptor<?> descriptor, String key) {
        return null;
    }

	@Override
	public String getCachedString(String uid, DefDescriptor<?> descriptor, String key, Callable<String> loader)
			throws QuickFixException, IOException {
		return null;
	}    

    @Override
    public void putCachedString(String uid, DefDescriptor<?> descriptor, String key, String value) {
    }

    @Override
    public List<ClientLibraryDef> getClientLibraries(String uid) {
        return null;
    }

	@Override
	public <D extends Definition> String hasAccess(
			DefDescriptor<?> referencingDescriptor, D def) {
		return null;
	}
	
	@Override
    public void setComponentClassLoaded(DefDescriptor<?> componentClass, Boolean isLoaded) {
		
    }
    
	@Override
    public Boolean getComponentClassLoaded(DefDescriptor<?> componentClass) {
    	return false;
    }
    
}

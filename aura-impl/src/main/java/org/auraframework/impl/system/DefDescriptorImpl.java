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
package org.auraframework.impl.system;

import java.io.IOException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.auraframework.Aura;
import org.auraframework.cache.Cache;
import org.auraframework.def.*;
import org.auraframework.impl.type.AuraStaticTypeDefRegistry;
import org.auraframework.impl.util.*;
import org.auraframework.impl.util.TypeParser.Type;
import org.auraframework.service.CachingService;
import org.auraframework.service.LoggingService;
import org.auraframework.throwable.AuraRuntimeException;
import org.auraframework.throwable.quickfix.QuickFixException;
import org.auraframework.util.AuraTextUtil;
import org.auraframework.util.json.Json;

/**
 */
public class DefDescriptorImpl<T extends Definition> implements DefDescriptor<T> {
    private static final long serialVersionUID = 3030118554156737974L;
    private final DefDescriptor<?> bundle;
    protected final String namespace;
    protected final String name;
    protected final String qualifiedName;
    protected final String descriptorName;
    protected final String prefix;
    protected final String nameParameters;
    protected final DefType defType;

    private final int hashCode;

    private static CachingService cSrv = Aura.getCachingService();

    public static String buildQualifiedName(String prefix, String namespace, String name) {
        if (namespace == null) {
            return String.format("%s://%s", prefix, name);
        }
        String format = MARKUP_PREFIX.equals(prefix) ? "%s://%s:%s" : "%s://%s.%s";
        return String.format(format, prefix, namespace, name);
    }

    private static String buildDescriptorName(String prefix, String namespace, String name) {
        if (namespace == null) {
            return String.format("%s", name);
        }
        String format = MARKUP_PREFIX.equals(prefix) ? "%s:%s" : "%s.%s";
        return String.format(format, namespace, name);
    }

    protected DefDescriptorImpl(DefDescriptor<?> associate, Class<T> defClass, String newPrefix) {
        LoggingService loggingService = Aura.getLoggingService();

        loggingService.startTimer(LoggingService.TIMER_DEF_DESCRIPTOR_CREATION);
        try {
            this.bundle = null;
            this.defType = DefType.getDefType(defClass);
            this.prefix = newPrefix;
            this.name = associate.getName();
            this.namespace = associate.getNamespace();
            this.qualifiedName = buildQualifiedName(prefix, namespace, name);
            this.descriptorName = buildDescriptorName(prefix, namespace, name);
            int pos = name.indexOf('<');
            this.nameParameters = pos >= 0 ? name.substring(pos).replaceAll("\\s", "") : null;
            this.hashCode = createHashCode();
        } finally {
            loggingService.stopTimer(LoggingService.TIMER_DEF_DESCRIPTOR_CREATION);
        }
        loggingService.incrementNum(LoggingService.DEF_DESCRIPTOR_COUNT);
    }

    private DefDescriptorImpl(String qualifiedName, Class<T> defClass, DefDescriptor<?> bundle) {
        this.bundle = bundle;
        LoggingService loggingService = Aura.getLoggingService();
        loggingService.startTimer(LoggingService.TIMER_DEF_DESCRIPTOR_CREATION);
        try {
            this.defType = DefType.getDefType(defClass);
            if (AuraTextUtil.isNullEmptyOrWhitespace(qualifiedName)) {
                throw new AuraRuntimeException("QualifiedName is required for descriptors");
            }

            String prefix = null;
            String namespace = null;
            String name = null;
            String nameParameters = null;

            switch (defType) {
            case CONTROLLER:
            case TESTSUITE:
            case MODEL:
            case RENDERER:
            case HELPER:
            case STYLE:
            case FLAVORED_STYLE:
            case RESOURCE:
            case TYPE:
            case PROVIDER:
            case TOKEN_DESCRIPTOR_PROVIDER:
            case TOKEN_MAP_PROVIDER:
            case INCLUDE:
                Type clazz = TypeParser.parseClass(qualifiedName);
                if (clazz != null) {
                    prefix = clazz.prefix;
                    namespace = clazz.namespace;
                    name = clazz.name;
                    
                    if (clazz.nameParameters != null 
                        && defType == org.auraframework.def.DefDescriptor.DefType.TYPE) {

                        nameParameters = clazz.nameParameters;
                    }
                } else {
                    throw new AuraRuntimeException(String.format("Invalid Descriptor Format: %s[%s]", qualifiedName, defType.toString()));
                }

                break;
            // subtypes
            case ACTION:
            case DESCRIPTION:
                throw new AuraRuntimeException(
                        String.format("%s descriptor must be a subdef: %s", defType.name(), qualifiedName));
            case ATTRIBUTE:
            case METHOD:
            case REQUIRED_VERSION:
            case TESTCASE:
            case TOKEN:
            case TOKENS_IMPORT:
            case ATTRIBUTE_DESIGN:
            case DESIGN_TEMPLATE:
            case DESIGN_TEMPLATE_REGION:
            case DESIGN_LAYOUT:
            case DESIGN_LAYOUT_SECTION:
            case DESIGN_LAYOUT_SECTION_ITEMS:
            case DESIGN_LAYOUT_SECTION_ITEMS_ATTRIBUTE:
            case DESIGN_LAYOUT_SECTION_ITEMS_COMPONENT:
            case DESIGN_OPTION:
            case INCLUDE_REF:
            case FLAVOR_INCLUDE:
            case FLAVOR_DEFAULT:
                name = qualifiedName;
                break;
            case APPLICATION:
            case COMPONENT:
            case INTERFACE:
            case EVENT:
            case LIBRARY:
            case DOCUMENTATION:
            case EXAMPLE:
            case TOKENS:
            case DESIGN:
            case SVG:
            case FLAVOR_BUNDLE:
            case FLAVORS:
                Type tag = TypeParser.parseTag(qualifiedName);
                if (tag != null) {
                    // default the prefix to 'markup'
                    prefix = tag.prefix != null ? tag.prefix : MARKUP_PREFIX;
                    namespace = tag.namespace;
                    name = tag.name;
                    qualifiedName = buildQualifiedName(prefix, namespace, name);
                } else {
                    throw new AuraRuntimeException(String.format("Invalid Descriptor Format: %s[%s]", qualifiedName, defType.toString()));
                }

                break;
            }

            if (AuraTextUtil.isNullEmptyOrWhitespace(prefix)) {
                prefix = Aura.getContextService().getCurrentContext().getDefaultPrefix(defType);
                if (prefix != null) {
                    qualifiedName = buildQualifiedName(prefix, namespace, name);
                }
            }
            this.qualifiedName = qualifiedName;
            this.descriptorName = buildDescriptorName(prefix, namespace, name);
            this.prefix = prefix;
            this.namespace = namespace;
            this.name = name;
            this.hashCode = createHashCode();
            this.nameParameters = nameParameters;
        } finally {
            loggingService.stopTimer(LoggingService.TIMER_DEF_DESCRIPTOR_CREATION);
        }
        loggingService.incrementNum(LoggingService.DEF_DESCRIPTOR_COUNT);
    }

    protected DefDescriptorImpl(String qualifiedName, Class<T> defClass) {
        this(qualifiedName, defClass, null);
    }

    private int createHashCode() {
        return (bundle == null ? 0 : bundle.hashCode())
                + AuraUtil.hashCodeLowerCase(name, namespace, prefix, defType.ordinal());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getNamespace() {
        return this.namespace;
    }

    @Override
    public String getQualifiedName() {
        return this.qualifiedName;
    }

    @Override
    public String getDescriptorName() {
        return descriptorName;
    }

    @Override
    public DefType getDefType() {
        return this.defType;
    }

    @Override
    public String getNameParameters() {
        return nameParameters;
    }

    @Override
    public void serialize(Json json) throws IOException {
        json.writeValue(qualifiedName);
    }

    @Override
    public String toString() {
        return qualifiedName;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DefDescriptor) {
            DefDescriptor<?> e = (DefDescriptor<?>) o;
            return (bundle == e.getBundle() || (bundle != null && bundle.equals(e.getBundle())))
                    && getDefType() == e.getDefType() && name.equalsIgnoreCase(e.getName())
                    && (namespace == null ? e.getNamespace() == null : namespace.equalsIgnoreCase(e.getNamespace()))
                    && (prefix == null ? e.getPrefix() == null : prefix.equalsIgnoreCase(e.getPrefix()));
        }
        return false;
    }

    @Override
    public final int hashCode() {
        return hashCode;
    }

    /**
     * @return Returns the prefix.
     */
    @Override
    public String getPrefix() {
        return prefix;
    }

    @Override
    public DefDescriptor<?> getBundle() {
        return this.bundle;
    }

    /**
     * @return Returns isParameterized.
     */
    @Override
    public boolean isParameterized() {
        return nameParameters != null;
    }

    private static <E extends Definition> DefDescriptor<E> buildInstance(String qualifiedName,
            Class<E> defClass, DefDescriptor<?> bundle) {
        if (defClass == TypeDef.class && qualifiedName.indexOf("://") == -1) {
            TypeDef typeDef = AuraStaticTypeDefRegistry.INSTANCE.getInsensitiveDef(qualifiedName);
            if (typeDef != null) {
                @SuppressWarnings("unchecked")
                DefDescriptor<E> result = (DefDescriptor<E>) typeDef.getDescriptor();
                return result;
            }
        }

        return new DefDescriptorImpl<>(qualifiedName, defClass, bundle);
    }

    /**
     * FIXME: this method is ambiguous about wanting a qualified, simple, or descriptor name.
     *
     * @param name The simple String representation of the instance requested ("foo:bar" or "java://foo.Bar")
     * @param defClass The Interface's Class for the DefDescriptor being requested.
     * @return An instance of a AuraDescriptor for the provided tag
     */
    public static <E extends Definition> DefDescriptor<E> getInstance(String name, Class<E> defClass,
            DefDescriptor<?> bundle) {
        if (name == null || defClass == null) {
            throw new AuraRuntimeException("descriptor is null");
        }

        DescriptorKey dk = new DescriptorKey(name, defClass, bundle);

        Cache<DescriptorKey, DefDescriptor<? extends Definition>> cache =
                cSrv.getDefDescriptorByNameCache();

        @SuppressWarnings("unchecked")
        DefDescriptor<E> result = (DefDescriptor<E>) cache.getIfPresent(dk);
        if (result == null) {
            result = buildInstance(name, defClass, bundle);

            // Our input names may not be qualified, but we should ensure that
            // the fully-qualified is properly cached to the same object.
            // I'd like an unqualified name to either throw or be resolved first,
            // but that's breaking or non-performant respectively.
            if (!dk.getName().equals(result.getQualifiedName())) {
                DescriptorKey fullDK = new DescriptorKey(result.getQualifiedName(), defClass, result.getBundle());

                @SuppressWarnings("unchecked")
                DefDescriptor<E> fullResult = (DefDescriptor<E>) cache.getIfPresent(fullDK);
                if (fullResult == null) {
                    cache.put(fullDK, result);
                } else {
                    // We already had one, just under the proper name
                    result = fullResult;
                }
            }

            cache.put(dk, result);
        }

        return result;
    }

    /**
     * FIXME: this method is ambiguous about wanting a qualified, simple, or descriptor name.
     *
     * @param name The simple String representation of the instance requested ("foo:bar" or "java://foo.Bar")
     * @param defClass The Interface's Class for the DefDescriptor being requested.
     * @return An instance of a AuraDescriptor for the provided tag
     */
    public static <E extends Definition> DefDescriptor<E> getInstance(String name, Class<E> defClass) {
        return getInstance(name, defClass, null);
    }

    /**
     * Get an instance from name parts.
     *
     * @param name The simple String representation of the instance requested ("foo:bar" or "java://foo.Bar")
     * @param namespace The Interface's Class for the DefDescriptor being requested.
     * @return An instance of a AuraDescriptor for the provided tag
     */
    public static DefDescriptor<?> getInstance(@CheckForNull String prefix, @Nonnull String namespace,
            @Nonnull String name, @Nonnull DefType defType) {
        StringBuilder sb = new StringBuilder();
        if (AuraTextUtil.isNullEmptyOrWhitespace(prefix)) {
            prefix = Aura.getContextService().getCurrentContext().getDefaultPrefix(defType);
        }
        sb.append(prefix.toLowerCase());
        sb.append("://");
        sb.append(namespace);
        if (prefix.equals("markup")) {
            sb.append(":");
        } else {
            sb.append(".");
        }
        sb.append(name);
        return getInstance(sb.toString(), defType.getPrimaryInterface(), null);
    }

    /**
     * @see DefDescriptor#getDef()
     */
    @Override
    public T getDef() throws QuickFixException {
        return Aura.getDefinitionService().getDefinition(this);
    }

    public static <E extends Definition> DefDescriptor<E> getAssociateDescriptor(DefDescriptor<?> desc,
            Class<E> defClass, String newPrefix) {
        if (desc == null) {
            throw new AuraRuntimeException("descriptor is null");
        }
        return new DefDescriptorImpl<>(desc, defClass, newPrefix);
    }

    /**
     * @see DefDescriptor#exists()
     */
    @Override
    public boolean exists() {
        return Aura.getContextService().getCurrentContext().getDefRegistry().exists(this);
    }

    /**
     * Compares one {@link DefDescriptor} to another. Sorting uses (only) the qualified name, case insensitively. Per
     * {@link Comparable}'s spec, throws {@link ClassCastException} if {@code arg} is not a {@code DefDescriptor}.
     */
    @Override
    public int compareTo(DefDescriptor<?> other) {
        return compare(this, other);
    }

    /**
     * Helper method for various {@link DefDescriptor} subclasses to implement {@link #compareTo(DefDescriptor)}, since
     * interfaces aren't allowed to have static methods, and since {@code DefDescriptor} is an interface rather than an
     * abstract class.
     */
    public static int compare(DefDescriptor<?> dd1, DefDescriptor<?> dd2) {
        if (dd1 == dd2) {
            return 0;
        }

        if (dd1 == null) {
            return -1;
        }

        if (dd2 == null) {
            return 1;
        }

        int value;

        value = dd1.getQualifiedName().compareToIgnoreCase(dd2.getQualifiedName());
        if (value != 0) {
            return value;
        }

        value = dd1.getDefType().compareTo(dd2.getDefType());
        if (value != 0) {
            return value;
        }

        return compare(dd1.getBundle(), dd2.getBundle());
    }
}

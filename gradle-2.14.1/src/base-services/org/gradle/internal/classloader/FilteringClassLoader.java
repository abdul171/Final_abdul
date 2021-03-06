/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.classloader;

import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * A ClassLoader which hides all non-system classes, packages and resources. Allows certain non-system packages and classes to be declared as visible. By default, only the Java system classes,
 * packages and resources are visible.
 */
public class FilteringClassLoader extends ClassLoader implements ClassLoaderHierarchy {
    private static final ClassLoader EXT_CLASS_LOADER;
    private static final Set<String> SYSTEM_PACKAGES = new HashSet<String>();
    private final Set<String> packageNames = new HashSet<String>();
    private final Set<String> packagePrefixes = new HashSet<String>();
    private final Set<String> resourcePrefixes = new HashSet<String>();
    private final Set<String> resourceNames = new HashSet<String>();
    private final Set<String> classNames = new HashSet<String>();
    private final Set<String> disallowedClassNames = new HashSet<String>();
    private final Set<String> disallowedPackagePrefixes = new HashSet<String>();

    static {
        EXT_CLASS_LOADER = ClassLoader.getSystemClassLoader().getParent();
        JavaMethod<ClassLoader, Package[]> method = JavaReflectionUtil.method(ClassLoader.class, Package[].class, "getPackages");
        Package[] systemPackages = method.invoke(EXT_CLASS_LOADER);
        for (Package p : systemPackages) {
            SYSTEM_PACKAGES.add(p.getName());
        }
    }

    public FilteringClassLoader(ClassLoader parent) {
        super(parent);
    }

    public FilteringClassLoader(ClassLoader parent, Spec spec) {
        super(parent);
        packageNames.addAll(spec.packageNames);
        packagePrefixes.addAll(spec.packagePrefixes);
        resourceNames.addAll(spec.resourceNames);
        resourcePrefixes.addAll(spec.resourcePrefixes);
        classNames.addAll(spec.classNames);
        disallowedClassNames.addAll(spec.disallowedClassNames);
        disallowedPackagePrefixes.addAll(spec.disallowedPackagePrefixes);
    }

    public void visit(ClassLoaderVisitor visitor) {
        visitor.visitSpec(new Spec(classNames, packageNames, packagePrefixes, resourcePrefixes, resourceNames, disallowedClassNames, disallowedPackagePrefixes));
        visitor.visitParent(getParent());
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return EXT_CLASS_LOADER.loadClass(name);
        } catch (ClassNotFoundException ignore) {
            // ignore
        }

        if (!classAllowed(name)) {
            throw new ClassNotFoundException(name + " not found.");
        }

        Class<?> cl = super.loadClass(name, false);
        if (resolve) {
            resolveClass(cl);
        }

        return cl;
    }

    @Override
    protected Package getPackage(String name) {
        Package p = super.getPackage(name);
        if (p == null || !allowed(p)) {
            return null;
        }
        return p;
    }

    @Override
    protected Package[] getPackages() {
        List<Package> packages = new ArrayList<Package>();
        for (Package p : super.getPackages()) {
            if (allowed(p)) {
                packages.add(p);
            }
        }
        return packages.toArray(new Package[0]);
    }

    @Override
    public URL getResource(String name) {
        if (allowed(name)) {
            return super.getResource(name);
        }
        return EXT_CLASS_LOADER.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (allowed(name)) {
            return super.getResources(name);
        }
        return EXT_CLASS_LOADER.getResources(name);
    }

    private boolean allowed(String resourceName) {
        if (resourceNames.contains(resourceName)) {
            return true;
        }
        for (String resourcePrefix : resourcePrefixes) {
            if (resourceName.startsWith(resourcePrefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean allowed(Package pkg) {
        for (String packagePrefix : disallowedPackagePrefixes) {
            if (pkg.getName().startsWith(packagePrefix)) {
                return false;
            }
        }

        if (SYSTEM_PACKAGES.contains(pkg.getName())) {
            return true;
        }
        if (packageNames.contains(pkg.getName())) {
            return true;
        }
        for (String packagePrefix : packagePrefixes) {
            if (pkg.getName().startsWith(packagePrefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean classAllowed(String className) {
        if (disallowedClassNames.contains(className)) {
            return false;
        }
        for (String packagePrefix : disallowedPackagePrefixes) {
            if (className.startsWith(packagePrefix)) {
                return false;
            }
        }

        if (classNames.contains(className)) {
            return true;
        }
        for (String packagePrefix : packagePrefixes) {
            if (className.startsWith(packagePrefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Marks a package and all its sub-packages as visible. Also makes resources in those packages visible.
     *
     * @param packageName the package name
     */
    public void allowPackage(String packageName) {
        packageNames.add(packageName);
        packagePrefixes.add(packageName + ".");
        resourcePrefixes.add(packageName.replace('.', '/') + '/');
    }

    /**
     * Marks a single class as visible.
     *
     * @param clazz the class
     */
    public void allowClass(Class<?> clazz) {
        classNames.add(clazz.getName());
    }

    /**
     * Marks a single class as not visible.
     *
     * @param className the class name
     */
    public void disallowClass(String className) {
        disallowedClassNames.add(className);
    }

    /**
     * Marks a package and all its sub-packages as not visible. Does not affect resources in those packages.
     *
     * @param packagePrefix the package prefix
     */
    public void disallowPackage(String packagePrefix) {
        disallowedPackagePrefixes.add(packagePrefix + ".");
    }

    /**
     * Marks all resources with the given prefix as visible.
     *
     * @param resourcePrefix the resource prefix
     */
    public void allowResources(String resourcePrefix) {
        resourcePrefixes.add(resourcePrefix + "/");
    }

    /**
     * Marks a single resource as visible.
     *
     * @param resourceName the resource name
     */
    public void allowResource(String resourceName) {
        resourceNames.add(resourceName);
    }

    public static class Spec extends ClassLoaderSpec {

        final Set<String> packageNames;
        final Set<String> packagePrefixes;
        final Set<String> resourcePrefixes;
        final Set<String> resourceNames;
        final Set<String> classNames;
        final Set<String> disallowedClassNames;
        final Set<String> disallowedPackagePrefixes;


        public Spec(Collection<String> classNames, Collection<String> packageNames, Collection<String> packagePrefixes, Collection<String> resourcePrefixes, Collection<String> resourceNames, Collection<String> disallowedClassNames, Collection<String> disallowedPackagePrefixes) {
            this.classNames = new HashSet<String>(classNames);
            this.packageNames = new HashSet<String>(packageNames);
            this.packagePrefixes = new HashSet<String>(packagePrefixes);
            this.resourcePrefixes = new HashSet<String>(resourcePrefixes);
            this.resourceNames = new HashSet<String>(resourceNames);
            this.disallowedClassNames = new HashSet<String>(disallowedClassNames);
            this.disallowedPackagePrefixes = new HashSet<String>(disallowedPackagePrefixes);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != getClass()) {
                return false;
            }
            Spec other = (Spec) obj;
            return other.packageNames.equals(packageNames)
                    && other.packagePrefixes.equals(packagePrefixes)
                    && other.resourceNames.equals(resourceNames)
                    && other.resourcePrefixes.equals(resourcePrefixes)
                    && other.classNames.equals(classNames)
                    && other.disallowedClassNames.equals(disallowedClassNames)
                    && other.disallowedPackagePrefixes.equals(disallowedPackagePrefixes);
        }

        @Override
        public int hashCode() {
            return packageNames.hashCode()
                    ^ packagePrefixes.hashCode()
                    ^ resourceNames.hashCode()
                    ^ resourcePrefixes.hashCode()
                    ^ classNames.hashCode()
                    ^ disallowedClassNames.hashCode()
                    ^ disallowedPackagePrefixes.hashCode();
        }
    }
}

/*
 * The MIT License
 *
 * Copyright 2015 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.tinymavenproxy;

import com.google.inject.Inject;
import com.mastfrog.settings.Settings;
import com.mastfrog.url.Path;
import com.mastfrog.url.PathElement;
import com.mastfrog.url.URL;
import com.mastfrog.url.URLBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 *
 * @author Tim Boudreau
 */
public class Config implements Iterable<URL> {
    public static final String SETTINGS_KEY_MIRROR_URLS = "mirror";
    public static final String MAVEN_CACHE_DIR = "maven.dir";
    private static final String DEFAULT_URLS="https://repo.maven.apache.org/maven2/"
            + ",http://bits.netbeans.org/maven2/"
            + ",http://bits.netbeans.org/nexus/content/repositories/snapshots/"
            + ",https://timboudreau.com/builds/plugin/repository/everything/"
            + ",https://maven.java.net/content/groups/public/"
            + ",https://oss.sonatype.org/";

    private final URL[] urls;
    public final File dir;

    @Inject
    Config(Settings s) throws IOException {
        String[] u = s.getString(SETTINGS_KEY_MIRROR_URLS, DEFAULT_URLS).split(",");
        urls = new URL[u.length];
        for (int i = 0; i < u.length; i++) {
            String mirror = u[i];
            urls[i] = URL.parse(mirror);
            if (!urls[i].isValid()) {
                urls[i].getProblems().throwIfFatalPresent();
            }
        }
        String dirname = s.getString(MAVEN_CACHE_DIR);
        if (dirname == null) {
            File tmp = new File(System.getProperty("java.io.tmpdir"));
            dir = new File(tmp, "maven");
            if (dir.exists() && !dir.isDirectory()) {
                throw new IOException("Not a folder: " + dir);
            }
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new IOException("Could not create " + dir);
                }
            }
        } else {
            dir = new File(dirname);
            if (dir.exists() && !dir.isDirectory()) {
                throw new IOException("Not a folder: " + dir);
            }
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new IOException("Could not create " + dir);
                }
            }
        }
    }

    public Collection<URL> withPath(Path path) {
        List<URL> result = new ArrayList(urls.length);
        for (URL u : this) {
            URLBuilder b = URL.builder(u);
            for (PathElement p : path) {
                b.add(p);
            }
            result.add(b.create());
        }
        return result;
    }

    @Override
    public Iterator<URL> iterator() {
        return Arrays.asList(urls).iterator();
    }
}

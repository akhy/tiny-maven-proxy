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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.annotations.HttpCall;
import com.mastfrog.acteur.bunyan.ActeurBunyanModule;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.HEAD;
import com.mastfrog.acteur.preconditions.Description;
import com.mastfrog.acteur.preconditions.Methods;
import com.mastfrog.acteur.preconditions.PathRegex;
import com.mastfrog.acteur.server.ServerBuilder;
import static com.mastfrog.acteur.server.ServerModule.BYTEBUF_ALLOCATOR_SETTINGS_KEY;
import static com.mastfrog.acteur.server.ServerModule.HTTP_COMPRESSION;
import static com.mastfrog.acteur.server.ServerModule.DIRECT_ALLOCATOR;
import static com.mastfrog.acteur.server.ServerModule.EVENT_THREADS;
import static com.mastfrog.acteur.server.ServerModule.MAX_CONTENT_LENGTH;
import static com.mastfrog.acteur.server.ServerModule.PORT;
import static com.mastfrog.acteur.server.ServerModule.WORKER_THREADS;
import com.mastfrog.acteur.util.ServerControl;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_ASYNC_LOGGING;
import static com.mastfrog.bunyan.LoggingModule.SETTINGS_KEY_LOG_LEVEL;
import static com.mastfrog.giulius.SettingsBindings.BOOLEAN;
import static com.mastfrog.giulius.SettingsBindings.INT;
import static com.mastfrog.giulius.SettingsBindings.STRING;
import com.mastfrog.netty.http.client.HttpClient;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOption;
import java.io.IOException;

/**
 *
 * @author Tim Boudreau
 */
public class TinyMavenProxy extends AbstractModule {

    public static final String APPLICATION_NAME = "tiny-maven-proxy";
    public static final String SETTINGS_KEY_DOWNLOAD_THREADS = "download.threads";
    public static final String DOWNLOAD_LOGGER = "download";
    public static final String ACCESS_LOGGER = ActeurBunyanModule.ACCESS_LOGGER;
    public static final String ERROR_LOGGER = ActeurBunyanModule.ERROR_LOGGER;
    public static final String SETTINGS_KEY_DOWNLOAD_CHUNK_SIZE = "download.chunk.size";
    static final int DEFAULT_DOWNLOAD_CHUNK_SIZE = 1480;

    public static void main(String[] args) throws IOException, InterruptedException {
        Settings settings = new SettingsBuilder(APPLICATION_NAME)
                .add("application.name", APPLICATION_NAME)
                .add(HTTP_COMPRESSION, "true")
                .add(SETTINGS_KEY_DOWNLOAD_THREADS, "24")
                .add(SETTINGS_KEY_ASYNC_LOGGING, "true")
                .add(WORKER_THREADS, "4")
                .add(EVENT_THREADS, "1")
                .add(SETTINGS_KEY_LOG_LEVEL, "info")
                .add(MAX_CONTENT_LENGTH, "128") // we don't accept PUTs, no need for a big buffer
                .add(PORT, "5956")
                .add(BYTEBUF_ALLOCATOR_SETTINGS_KEY, DIRECT_ALLOCATOR)
                .addFilesystemAndClasspathLocations()
                .parseCommandLineArguments(args).build();
        ServerControl ctrl = new ServerBuilder(APPLICATION_NAME)
                .add(new TinyMavenProxy())
                .add(new ActeurBunyanModule(true).bindLogger(DOWNLOAD_LOGGER))
                .enableOnlyBindingsFor(BOOLEAN, INT, STRING)
                .add(settings)
                .withType(DownloadResult.class)
                .build().start();
        ctrl.await();
    }

    @Override
    protected void configure() {
        bind(HttpClient.class).toProvider(HttpClientProvider.class);
    }

    @HttpCall(order = Integer.MIN_VALUE)
    @PathRegex({"^favicon.ico$", "^.index.*"})
    @Methods({GET, HEAD})
    @Description("Sends 404 for /favicon.ico")
    static class FaviconPage extends Acteur {

        @Inject
        FaviconPage() {
            notFound();
        }
    }

    @Singleton
    static class HttpClientProvider implements Provider<HttpClient> {

        private final HttpClient client;

        @Inject
        HttpClientProvider(ByteBufAllocator alloc, @Named(SETTINGS_KEY_DOWNLOAD_THREADS) int downloadThreads) {
            client = HttpClient.builder()
                    .setUserAgent("TinyMavenProxy 1.0")
                    .followRedirects()
                    .setChannelOption(ChannelOption.ALLOCATOR, alloc)
                    .threadCount(downloadThreads)
                    .maxChunkSize(16384).build();
        }

        @Override
        public HttpClient get() {
            return client;
        }
    }
}

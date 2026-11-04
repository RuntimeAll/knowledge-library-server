package com.ruoyi.web.core.config;

import com.ruoyi.knowledge.publication.PublicationProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/** Serves only fingerprinted public-app code; publication content still passes the anonymous read gate. */
@Configuration
public class PublicationStaticResources implements WebMvcConfigurer {
    private final PublicationProperties properties;
    public PublicationStaticResources(PublicationProperties properties) { this.properties = properties; }
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (!properties.isEnabled() || properties.getStaticDirectory() == null || properties.getStaticDirectory().isBlank()) { return; }
        try {
            Path assets = Path.of(properties.getStaticDirectory()).toAbsolutePath().resolve("assets").toRealPath();
            if (!Files.isDirectory(assets)) { throw new IOException("assets is not a directory"); }
            registry.addResourceHandler("/publication-static/assets/*")
                    .addResourceLocations(assets.toUri().toString())
                    .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                    .resourceChain(false).addResolver(new PublishedAssetResolver(assets));
        } catch (IOException e) { throw new IllegalStateException("发布静态目录必须指向构建后的dist-publication目录", e); }
    }
    private static final class PublishedAssetResolver extends PathResourceResolver {
        private final Path root;
        private PublishedAssetResolver(Path root) { this.root = root; }
        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            if (!resourcePath.matches("[A-Za-z0-9][A-Za-z0-9._-]*-[A-Za-z0-9_-]{6,}\\.(?:js|css)") || resourcePath.contains("..")) { return null; }
            Path candidate = root.resolve(resourcePath);
            if (!Files.isRegularFile(candidate)) { return null; }
            Path real = candidate.toRealPath();
            if (!real.startsWith(root)) { return null; }
            return new FileSystemResource(real);
        }
    }
}

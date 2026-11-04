package com.ruoyi.web.core.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.ruoyi.knowledge.publication.PublicationProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class PublicationStaticResourcesTest {
    @TempDir Path temporary;
    @Configuration @EnableWebMvc static class Mvc { }
    @Test void onlyBuiltPublicJsCssCanBeReadFromConfiguredAssetsDirectory() throws Exception {
        Path build = Files.createDirectory(temporary.resolve("dist-publication"));
        Path assets = Files.createDirectory(build.resolve("assets"));
        Files.writeString(assets.resolve("publication-abcd1234.js"), "export const publicApp=true;");
        Files.writeString(assets.resolve("publication-abcd1234.css"), "body{color:#333}");
        Files.writeString(assets.resolve("publication-abcd1234.js.map"), "private source map");
        Files.writeString(build.resolve("publication.html"), "page shell must be gated");
        Path outside = Files.writeString(temporary.resolve("private.js"), "private");
        Files.createSymbolicLink(assets.resolve("outside-abcd1234.js"), outside);
        PublicationProperties properties = new PublicationProperties(); properties.setEnabled(true); properties.setStaticDirectory(build.toString());
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext()); context.register(Mvc.class, PublicationStaticResources.class);
            context.addBeanFactoryPostProcessor(factory -> factory.registerSingleton("publicationProperties", properties));
            context.refresh(); var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(get("/publication-static/assets/publication-abcd1234.js"))
                    .andExpect(status().isOk()).andExpect(content().string("export const publicApp=true;"))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("immutable")));
            mvc.perform(get("/publication-static/assets/publication-abcd1234.css")).andExpect(status().isOk());
            mvc.perform(get("/publication-static/assets/publication-abcd1234.js.map")).andExpect(status().isNotFound());
            mvc.perform(get("/publication-static/assets/outside-abcd1234.js")).andExpect(status().isNotFound());
            mvc.perform(get("/publication-static/publication.html")).andExpect(status().isNotFound());
            mvc.perform(get("/publication-static/assets/../publication.html")).andExpect(status().isNotFound());
        }
    }
}

package com.ruoyi.web.core.docs;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Layer boundaries checked against compiled production bytecode; does not claim SQL performance. */
class KnowledgeArchitectureTest {
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.ruoyi.web.controller.knowledge", "com.ruoyi.knowledge");

    @Test
    void controllersDoNotAccessPersistence() {
        noClasses().that().resideInAPackage("..controller.knowledge..")
                .should().dependOnClassesThat().resideInAnyPackage("..mapper..", "java.sql..", "javax.sql..",
                        "org.springframework.jdbc..")
                .check(classes);
    }

    @Test
    void businessModuleDoesNotDependOnManagementWebLayer() {
        noClasses().that().resideInAPackage("com.ruoyi.knowledge..")
                .should().dependOnClassesThat().resideInAnyPackage("com.ruoyi.web..")
                .check(classes);
    }

    @Test
    void mappersDoNotDependOnBusinessServices() {
        noClasses().that().resideInAPackage("..mapper..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..controller..")
                .check(classes);
    }
}

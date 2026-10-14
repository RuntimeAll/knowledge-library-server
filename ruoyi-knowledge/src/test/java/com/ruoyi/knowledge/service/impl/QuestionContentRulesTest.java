package com.ruoyi.knowledge.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuestionContentRulesTest {
    @Test
    void markupWhitespaceAndExecutableCodeAreNotEducationalContent() {
        assertFalse(QuestionContentRules.hasContent(null));
        assertFalse(QuestionContentRules.hasContent("<p>&nbsp;\u200B</p><br>"));
        assertFalse(
                QuestionContentRules.hasContent(
                        "<script>alert(1)</script><style>p{color:red}</style>"));
        assertFalse(QuestionContentRules.hasContent("<p>\\( \\)</p>"));
    }

    @Test
    void MathematicsAndFiguresAreValidEvenWithoutProse() {
        assertTrue(QuestionContentRules.hasContent("<p>\\(x^2+1\\)</p>"));
        assertTrue(QuestionContentRules.hasContent("<script type='math/tex'>x+1</script>"));
        assertTrue(QuestionContentRules.hasContent("<img src='kb-asset:10'>"));
        assertTrue(QuestionContentRules.hasContent("<svg><path d='M1 2L3 4'/></svg>"));
    }
}

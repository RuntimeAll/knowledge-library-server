package com.ruoyi.knowledge.publication;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.ruoyi.knowledge.mapper.KbAgentSubmissionMapper;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.mapper.PublicationMapper;
import com.ruoyi.knowledge.publication.PublicationModels.FileInput;
import com.ruoyi.knowledge.publication.PublicationModels.FrozenQuestion;
import com.ruoyi.knowledge.publication.PublicationModels.ImageInput;
import com.ruoyi.knowledge.publication.PublicationModels.Input;
import com.ruoyi.knowledge.publication.PublicationModels.ProductFact;
import com.ruoyi.knowledge.publication.PublicationModels.ProductInput;
import com.ruoyi.knowledge.publication.PublicationModels.ShareInput;
import com.ruoyi.knowledge.review.KbPublicationReviewBridge;
import com.ruoyi.knowledge.review.KbReviewGate;
import com.ruoyi.knowledge.review.KbReviewService;
import com.ruoyi.knowledge.storage.IKbAssetService;
import com.ruoyi.knowledge.storage.domain.KbAsset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** File-courseware exceptions must remain explicit and backed by existing server facts. */
class PublicationCoursewareTest {
    @Test void explicitCoursewareCanPassWithoutInventingQuestionsOrApprovals() {
        Fixture f = new Fixture();
        assertDoesNotThrow(() -> f.graph.validate(f.input));
        assertTrue(f.checks.materialIssues(f.input).isEmpty());
        assertTrue(f.input.products.get(0).questions.isEmpty());
    }

    @Test void emptyWorksheetAndUnknownCoursewareStillRequireVerifiedQuestionLists() {
        Fixture f = new Fixture();
        f.fact.resourceType = "WORKSHEET";
        assertThrows(PublicationException.class, () -> f.graph.validate(f.input));
        assertFalse(f.checks.materialIssues(f.input).isEmpty());
        f.fact.resourceType = "COURSEWARE";
        f.input.products.get(0).questionListStatus = "UNKNOWN";
        assertTrue(f.checks.materialIssues(f.input).stream().anyMatch(i -> "QUESTION_LIST_REQUIRED".equals(i.code())));
        f.input.products.get(0).questionListStatus = "VERIFIED";
        assertThrows(PublicationException.class, () -> f.graph.validate(f.input));
    }

    @Test void coursewareWithFrozenQuestionsCannotDiscardThemUsingTheFileDeclaration() {
        Fixture f = new Fixture();
        FrozenQuestion frozen = new FrozenQuestion(); frozen.productId = 1L; frozen.questionId = 7L; frozen.seq = 1;
        when(f.mapper.frozenQuestions(anyList())).thenReturn(List.of(frozen));
        assertThrows(PublicationException.class, () -> f.graph.validate(f.input));
    }

    @Test void declarationNeedsAnExplicitZeroAndEvidence() {
        Fixture f = new Fixture(); ProductInput p = f.input.products.get(0);
        p.expectedQuestionCount = null;
        assertThrows(PublicationException.class, () -> f.graph.validate(f.input));
        p.expectedQuestionCount = 0; p.note = null;
        assertThrows(PublicationException.class, () -> f.graph.validate(f.input));
        p.note = "已核实课件文件";
        assertDoesNotThrow(() -> f.graph.validate(f.input));
    }

    @Test void coursewareDoesNotRelaxImageOrBaiduFileVerification() {
        Fixture f = new Fixture();
        f.input.baiduShares.get(0).files.get(0).mappingStatus = "PENDING";
        assertTrue(f.checks.materialIssues(f.input).stream().anyMatch(i -> "BAIDU_MAPPING_REQUIRED".equals(i.code())));
        f.input.baiduShares.get(0).files.get(0).mappingStatus = "VERIFIED";
        f.input.images = List.of();
        assertTrue(f.checks.materialIssues(f.input).stream().anyMatch(i -> "MATERIAL_REQUIRED".equals(i.code())));
    }

    @Test void changingTheLiveCatalogTypeRevokesTheFileExceptionOnRecheck() {
        Fixture f = new Fixture();
        assertTrue(f.checks.materialIssues(f.input).isEmpty());
        f.fact.resourceType = "UNKNOWN";
        assertEquals("INVALID_REFERENCE", f.checks.materialIssues(f.input).get(0).code());
    }

    private static class Fixture {
        final PublicationMapper mapper = mock(PublicationMapper.class);
        final ProductFact fact = new ProductFact();
        final Input input = new Input();
        final PublicationGraph graph;
        final PublicationCheckService checks;

        Fixture() {
            var properties = new PublicationProperties(); properties.setEnabled(true);
            var support = new PublicationSupport(mock(PlatformTransactionManager.class), new KbReviewGate(), properties);
            var assets = mock(IKbAssetService.class);
            graph = new PublicationGraph(mapper, support, assets);
            var service = new PublicationService(mapper, support, graph, mock(KbAgentSubmissionMapper.class));
            checks = new PublicationCheckService(service, mock(KbPublicationReviewBridge.class),
                    mock(KbQuestionMapper.class), mock(KbReviewService.class));
            fact.id = 1L; fact.assetId = 2L; fact.resourceType = "COURSEWARE";
            when(mapper.productFacts(anyList())).thenReturn(List.of(fact));
            when(mapper.frozenQuestions(anyList())).thenReturn(List.of());
            KbAsset html = new KbAsset(); html.setAssetId(2L); html.setContentType("text/html");
            KbAsset image = new KbAsset(); image.setAssetId(3L); image.setContentType("image/png");
            when(assets.requireReady(2L)).thenReturn(html); when(assets.requireReady(3L)).thenReturn(image);
            ProductInput product = new ProductInput(); product.productId = 1L; product.sortOrder = 1;
            product.questionListStatus = "NOT_APPLICABLE"; product.expectedQuestionCount = 0;
            product.questions = List.of(); product.note = "已核实为无冻结题单的交互课件文件";
            input.title = "课件"; input.postTitle = "课件介绍"; input.postBody = "实际功能介绍";
            input.products = List.of(product); input.referenceSources = List.of();
            ImageInput img = new ImageInput(); img.assetId = 3L; img.sortOrder = 1; input.images = List.of(img);
            ShareInput share = new ShareInput(); share.shareUrl = "https://pan.baidu.com/s/test-courseware"; share.sortOrder = 1;
            FileInput file = new FileInput(); file.productId = 1L; file.remoteFileName = "courseware.html";
            file.remoteFileSize = 10L; file.mappingStatus = "VERIFIED"; file.verificationNote = "已下载回核SHA-256";
            share.files = List.of(file); input.baiduShares = List.of(share);
        }
    }
}

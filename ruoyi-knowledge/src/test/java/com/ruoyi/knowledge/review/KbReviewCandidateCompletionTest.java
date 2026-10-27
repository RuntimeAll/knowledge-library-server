package com.ruoyi.knowledge.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ruoyi.knowledge.domain.KbOccurrence;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.html.HtmlMetadata;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.KbRecordHtmlConverter;
import com.ruoyi.knowledge.importing.dto.KbRecordsRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/** Real MyBatis/InnoDB evidence sequencing; pipeline HTTP and actual import-worker execution are separate acceptance. */
@EnabledIfEnvironmentVariable(named = "KB_REVIEW_TEST_JDBC_URL", matches = ".+")
class KbReviewCandidateCompletionTest {
    @TempDir Path directory;

    @Test void candidateExceptionSurvivesOnlyTheExactFirstSourceCompletionAndCanApprove() throws Exception {
        var fixture = fixture();
        try {
            long id = importCandidate(fixture);
            assertNotNull(fixture.proofs.matching(full(fixture,id)));
            assertFalse(fixture.service.readiness(id).canApprove(), "unverified source still prevents normal approval");
            verify(fixture,id,7,0);
            assertEquals(0L,fixture.questions.selectById(id).getRowVersion());
            assertNotNull(fixture.proofs.matching(full(fixture,id)), "same candidate's first exact verification preserves count-only proof");
            assertTrue(fixture.service.readiness(id).canApprove());
            fixture.service.act(id,"APPROVE",new ReviewModels.Command("completed-approve",0L,"已核对完整来源和答案",true,null,fixture.service.readiness(id).contentHash()));
            assertTrue(fixture.service.readiness(id).eligible());
            verify(fixture,id,7,1);
            assertEquals("DRAFT",fixture.questions.selectById(id).getStatus());
            assertNull(fixture.proofs.matching(full(fixture,id)), "later source writes never silently renew old proof");
        } finally { fixture.close(); }
    }

    @Test void wrongPositionAndThenCorrectingItCannotRenewOldCandidateException() throws Exception {
        var fixture=fixture();
        try {
            long id=importCandidate(fixture);
            verify(fixture,id,8,0);
            assertNull(fixture.proofs.matching(full(fixture,id)));
            verify(fixture,id,7,1);
            assertNull(fixture.proofs.matching(full(fixture,id)), "only a single exact UNVERIFIED to VERIFIED completion is allowed");
        } finally { fixture.close(); }
    }

    @Test void changedSourceBytesCannotReuseCandidateExceptionDuringCompletion() throws Exception {
        var fixture=fixture();
        try {
            long id=importCandidate(fixture);
            verify(fixture,id,7,0);
            fixture.jdbc.update("UPDATE kb_asset SET sha256=? WHERE asset_id=?","e".repeat(64),id);
            assertNull(fixture.proofs.matching(full(fixture,id)));
            assertFalse(fixture.service.readiness(id).canApprove());
        } finally { fixture.close(); }
    }

    private long importCandidate(KbReviewMysqlIntegrationTest fixture) throws Exception {
        var method=KbReviewMysqlIntegrationTest.class.getDeclaredMethod("seed");method.setAccessible(true);
        long id=(long)method.invoke(fixture);
        var metadata=new HtmlMetadata("two computations","MATH","CALCULATION","MEDIUM",List.of(id),
                null,null,null,null,null,"EXERCISE","PLAIN","GROUP","FLOW",null);
        var record=new KbRecordsRequest.Record("completion-key","create",null,null,null,metadata,
                "计算下面各题。",null,null,null,null,null,1,
                List.of(new KbRecordsRequest.Item("a","1+1=",null,"2",null,null,null),
                        new KbRecordsRequest.Item("b","2+2=",null,"4",null,null,null)),null);
        var proxy=mock(KbCandidateProxy.class);
        var candidate=new KbCandidateProxy.Candidate("pipeline-candidate/v1","completion-run","g","original",
                "teacher","fixture","completion-candidate",1,"completion-key","b".repeat(64),"{}",record,Long.toString(id),
                new KbCandidateProxy.SourceLocator("9","7","1",null),
                List.of(new KbCandidateProxy.ArtifactRef("original","c".repeat(64),"application/pdf",10L)),
                "2026-10-27","CODEX",true,null);
        when(proxy.get("completion-run","completion-candidate")).thenReturn(candidate);
        var converter=new KbRecordHtmlConverter();var codec=new KbHtmlCodec();
        var proofService=new KbReviewProofs(fixture.ledger,fixture.gate,fixture.mapper,fixture.questions,proxy,converter,codec);
        var proof=proofService.grant(new ReviewModels.ExceptionCommand("completion-exception","真实母题只有两小题",
                null,null,"completion-run","completion-candidate","b".repeat(64)));
        byte[] html=converter.convert(new KbRecordsRequest(KbRecordsRequest.SCHEMA,"fixture-import",id,"fixture",List.of(record),List.of()));
        var article=codec.readArticle(codec.readArticles(html).get(0));
        fixture.jdbc.update("UPDATE kb_question SET title=?,stem_html=?,answer_html=?,analysis_html=?,question_type='CALCULATION',unit_kind='GROUP',item_count=2,creation_kind='EXTRACTED' WHERE id=?",
                metadata.title(),article.stem().html(),article.answer().html(),article.analysis()==null?"":article.analysis().html(),id);
        fixture.jdbc.update("UPDATE kb_source SET source_kind='TEXTBOOK' WHERE id=?",id);
        fixture.jdbc.update("INSERT INTO kb_asset VALUES(?,'READY',?,10)",id,"c".repeat(64));
        fixture.jdbc.update("INSERT INTO kb_source_asset VALUES(?,?,'ORIGINAL')",id,id);
        var input=full(fixture,id);input.setImportClientKey("completion-key");input.setGranularityExceptionId(proof.exceptionId());
        input.setCandidateArticleHash(article.candidateArticleHash());input.setImportSourceId(id);
        proofService.validateNewUnit(input);
        proofService.bindImport(full(fixture,id),input,"teacher",article.fingerprint());
        fixture.jdbc.update("INSERT INTO kb_import_identity VALUES('completion-key','teacher',?,?)",id,article.fingerprint());
        return id;
    }

    private static void verify(KbReviewMysqlIntegrationTest fixture,long id,int page,long version) throws Exception {
        var method=KbReviewMysqlIntegrationTest.class.getDeclaredMethod("context");method.setAccessible(true);
        try(var context=(org.springframework.context.annotation.AnnotationConfigApplicationContext)method.invoke(fixture)) {
            var occurrence=new KbOccurrence();occurrence.setId(id);occurrence.setRowVersion(version);
            occurrence.setQuestionId(id);occurrence.setSourceId(id);occurrence.setQuestionNo("1");occurrence.setPageFrom(page);occurrence.setEvidenceAssetId(id);
            context.getBean(KbReviewMysqlIntegrationTest.TestMutation.class).firstSourceVerification(occurrence);
        }
    }
    private KbReviewMysqlIntegrationTest fixture() throws Exception {
        var fixture=new KbReviewMysqlIntegrationTest();fixture.directory=directory;fixture.setup();return fixture;
    }
    private static KbQuestion full(KbReviewMysqlIntegrationTest fixture,long id) {
        var question=fixture.questions.selectById(id);question.setKpIds(fixture.questions.selectKpIds(id));question.setAssetIds(fixture.questions.selectAssetIds(id));return question;
    }
}

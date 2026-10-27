package com.ruoyi.knowledge.review;

import com.ruoyi.knowledge.mapper.KbReviewMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInterceptor;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbQuestion;
import com.ruoyi.knowledge.dto.KbQuestionQuery;
import com.ruoyi.knowledge.html.KbHtmlCodec;
import com.ruoyi.knowledge.importing.KbRecordHtmlConverter;
import com.ruoyi.knowledge.mapper.KbQuestionMapper;
import com.ruoyi.knowledge.service.impl.KnowledgeDictionary;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/** Opt-in: refuses cloud/legacy ports and uses only the dedicated disposable fixture schema. */
@EnabledIfEnvironmentVariable(named = "KB_REVIEW_TEST_JDBC_URL", matches = ".+")
class KbReviewMysqlIntegrationTest {
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 100);
    @TempDir Path directory;
    DriverManagerDataSource source;
    JdbcTemplate jdbc;
    KbQuestionMapper questions;
    KbReviewMapper mapper;
    KbReviewLedger ledger;
    KbReviewGate gate;
    KbReviewProofs proofs;
    KbReviewService service;
    DataSourceTransactionManager manager;
    org.mybatis.spring.SqlSessionTemplate session;
    ObservedDataSource observed;

    @BeforeEach
    void setup() throws Exception {
        String url = System.getenv("KB_REVIEW_TEST_JDBC_URL");
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:13308/prd010_review_test(?:\\?.*)?"),
                "Only the explicit fresh local fixture is permitted; never 13307 or 3307");
        source = new DriverManagerDataSource(url, "root", "");
        jdbc = new JdbcTemplate(source);
        createFixture();
        observed = new ObservedDataSource(source);
        manager = new DataSourceTransactionManager(observed);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(observed);
        factory.setMapperLocations(new ClassPathResource("mapper/knowledge/KbQuestionMapper.xml"),
                new ClassPathResource("mapper/knowledge/KbReviewMapper.xml"), new ClassPathResource("mapper/knowledge/KbOccurrenceMapper.xml"));
        var paging = new PageInterceptor();
        Properties properties = new Properties(); properties.setProperty("helperDialect", "mysql");
        properties.setProperty("reasonable", "false"); paging.setProperties(properties);
        factory.setPlugins(paging);
        session = new SqlSessionTemplate(factory.getObject());
        questions = session.getMapper(KbQuestionMapper.class);
        mapper = session.getMapper(KbReviewMapper.class);
        ledger = new KbReviewLedger(directory.toString());
        gate = new KbReviewGate();
        proofs = new KbReviewProofs(ledger, gate, mapper, questions, mock(KbCandidateProxy.class),
                new KbRecordHtmlConverter(), new KbHtmlCodec());
        service = reviews(manager);
        login();
    }

    private KbReviewService reviews(PlatformTransactionManager transactions) {
        return new KbReviewService(ledger, gate, questions, mapper, mock(KnowledgeDictionary.class), proofs, transactions, true);
    }

    @AfterEach void close() throws Exception { PageHelper.clearPage(); SecurityContextHolder.clearContext(); if (ledger != null) ledger.close(); }

    @Test void approvalProjectionFiltersBeforeCountAndLimitIncludingEmptyPages() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 43; i++) {
            long id = seed(); ids.add(id);
            if (i % 2 == 0) service.act(id, "APPROVE", command("approve-" + id, 0));
            else jdbc.update("UPDATE kb_question SET status='READY' WHERE id=?", id); // unapproved legacy fixture
        }
        String projection = service.approvalJson();
        for (int size : List.of(10, 20)) {
            List<Long> actual = new ArrayList<>();
            for (int pageNumber = 1; pageNumber <= 5; pageNumber++) {
                KbQuestionQuery query = new KbQuestionQuery(); query.setPurpose("NORMAL"); query.setIds(ids); query.setApprovalJson(projection);
                PageHelper.startPage(pageNumber, size).setReasonable(false);
                Page<KbQuestion> page = (Page<KbQuestion>) questions.selectList(query);
                assertEquals(22, page.getTotal());
                actual.addAll(page.stream().map(KbQuestion::getId).toList());
                if (pageNumber > (22 + size - 1) / size) assertTrue(page.isEmpty());
            }
            assertEquals(ids.stream().filter(id -> ids.indexOf(id) % 2 == 0).sorted(java.util.Comparator.reverseOrder()).toList(), actual);
        }
        long stale = ids.get(0);
        jdbc.update("UPDATE kb_source SET row_version=row_version+1 WHERE id=?", stale);
        assertFalse(mapper.usableIds(ids, projection).contains(stale), "live dependency stamp must filter old evidence before pagination");
        assertTrue(mapper.usableIds(ids, "[]").isEmpty());
    }

    @Test void lostCommitAcknowledgementRecoversWithoutAnotherStateChange() {
        long id = seed();
        PlatformTransactionManager lossy = new PlatformTransactionManager() {
            boolean fail = true;
            public TransactionStatus getTransaction(TransactionDefinition definition) { return manager.getTransaction(definition); }
            public void commit(TransactionStatus status) {
                manager.commit(status);
                if (fail) { fail = false; throw new org.springframework.transaction.TransactionSystemException("injected lost commit ACK"); }
            }
            public void rollback(TransactionStatus status) { manager.rollback(status); }
        };
        String operation = "lost-ack-" + UUID.randomUUID();
        ReviewModels.Receipt first = reviews(lossy).act(id, "APPROVE", command(operation, 0));
        assertEquals("CONFIRMED", first.outcome());
        assertEquals("1", first.rowVersion());
        assertEquals(first, service.act(id, "APPROVE", command(operation, 0)));
        assertEquals(1L, questions.selectById(id).getRowVersion());
    }

    @Test void recoveryWaitsForOriginalInnoDbTransactionAndDoesNotAssumeRollback() throws Exception {
        long id = seed();
        KbQuestion question = full(id);
        ReviewModels.Snapshot before = snapshot(question);
        ReviewModels.Snapshot after = new ReviewModels.Snapshot(Long.toString(id), "1", "READY", before.contentHash(), before.facts());
        ledger.prepare(new ReviewModels.Prepared("inflight", "a".repeat(64), "APPROVE", "teacher", "fixture", KbReviewService.POLICY, before, after, null));
        Connection original = source.getConnection(); original.setAutoCommit(false);
        try (var update = original.prepareStatement("UPDATE kb_question SET status='READY',row_version=1 WHERE id=?")) { update.setLong(1,id); update.executeUpdate(); }
        CountDownLatch done = new CountDownLatch(1);
        var failure = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        Thread restart = new Thread(() -> {
            gate.enter();
            try { service.recover(); } catch (Throwable error) { failure.set(error); }
            finally { gate.leave(); done.countDown(); }
        });
        restart.start();
        assertFalse(done.await(150, TimeUnit.MILLISECONDS), "locking recovery must wait for old server transaction");
        original.commit(); original.close();
        assertTrue(done.await(3, TimeUnit.SECONDS)); restart.join();
        assertNull(failure.get());
        assertEquals("CONFIRMED", ledger.terminal("inflight").outcome());
    }

    @Test void preparedRollbackCannotBeMisidentifiedAsLaterSameTargetVersion() {
        long id = seed(); ReviewModels.Snapshot before = snapshot(full(id));
        ReviewModels.Snapshot after = new ReviewModels.Snapshot(Long.toString(id), "1", "READY", before.contentHash(), before.facts());
        ledger.prepare(new ReviewModels.Prepared("operation-a", "a".repeat(64), "APPROVE", "teacher", "fixture", KbReviewService.POLICY, before, after, null));
        gate.enter(); try { service.recover(); } finally { gate.leave(); }
        assertEquals("ABORTED", ledger.terminal("operation-a").outcome());
        service.act(id, "APPROVE", command("operation-b", 0));
        assertEquals("ABORTED", ledger.terminal("operation-a").outcome());
        assertEquals("CONFIRMED", ledger.terminal("operation-b").outcome());
    }

    @Test void unknownCurrentImageBlocksRecoveryAndNormalUse() {
        long id = seed(); ReviewModels.Snapshot before = snapshot(full(id));
        ledger.prepare(new ReviewModels.Prepared("unknown", "a".repeat(64), "APPROVE", "teacher", "fixture", KbReviewService.POLICY,
                before, new ReviewModels.Snapshot(Long.toString(id), "1", "READY", before.contentHash(), before.facts()), null));
        jdbc.update("UPDATE kb_question SET answer_html='changed',row_version=2 WHERE id=?", id);
        gate.enter(); try { assertThrows(ServiceException.class, () -> service.recover()); } finally { gate.leave(); }
        assertThrows(ServiceException.class, () -> service.approvalJson());
        assertNull(ledger.terminal("unknown"));
    }

    @Test void actualSpringReadAdvicePinsCountRowsAndConstantPageAssociationQueries() {
        List<Long> ids = new ArrayList<>();
        for (int i=0;i<25;i++) { long id=seed(); ids.add(id); service.act(id,"APPROVE",command("page-"+id,0)); }
        try (var context = context()) {
            var listService = context.getBean(com.ruoyi.knowledge.service.impl.KbQuestionServiceImpl.class);
            for (int size : List.of(10,20)) {
                KbQuestionQuery query=new KbQuestionQuery();query.setPurpose("NORMAL");query.setIds(ids);
                PageHelper.startPage(1,size).setReasonable(false); observed.sql.clear();
                Page<KbQuestion> page=(Page<KbQuestion>) listService.list(query);
                assertEquals(25,page.getTotal());assertEquals(size,page.size());
                assertEquals(5,observed.sql.size(),"COUNT, main page and three bounded association queries");
            }
            long changed=ids.get(ids.size()-1);
            observed.afterCount=()->jdbc.update("UPDATE kb_question SET status='DRAFT',row_version=row_version+1 WHERE id=?",changed);
            KbQuestionQuery query=new KbQuestionQuery();query.setPurpose("NORMAL");query.setIds(ids);
            PageHelper.startPage(1,20).setReasonable(false);
            Page<KbQuestion> page=(Page<KbQuestion>) listService.list(query);
            assertEquals(25,page.getTotal());
            assertTrue(page.stream().anyMatch(q->q.getId()==changed),"page must share COUNT's earlier RR snapshot");
            assertEquals("DRAFT",questions.selectById(changed).getStatus());
        }
    }

    @Test void outerSpringGateCoversRequiresNewRollbackAndDependencyInvalidation() {
        long id=seed();service.act(id,"APPROVE",command("before-dependency",0));
        try (var context=context()) {
            TestMutation mutation=context.getBean(TestMutation.class);
            assertThrows(IllegalStateException.class,()->mutation.importFailure(id));
            assertEquals("READY",questions.selectById(id).getStatus());
            assertEquals(1L,questions.selectById(id).getRowVersion());
            var sourceChange=new com.ruoyi.knowledge.domain.KbSource();sourceChange.setId(id);
            mutation.sourceChange(sourceChange);
            assertEquals("DRAFT",questions.selectById(id).getStatus());
            assertEquals(2L,questions.selectById(id).getRowVersion());
            var outside=new org.springframework.transaction.support.TransactionTemplate(manager);
            assertThrows(IllegalStateException.class,()->outside.execute(status->{mutation.importFailure(id);return null;}));
        }
    }

    @Test void explicitStopKeepsChildrenButContentErrorAtomicallyDraftsThemAndReplays() {
        long parent=seed(), child=seed();
        jdbc.update("INSERT INTO kb_question_derivation(parent_question_id,child_question_id,is_primary,operation,change_note,preserved_note) VALUES(?,?,true,'CHANGE_VALUES','fixture change','fixture preserve')",parent,child);
        service.act(parent,"APPROVE",command("parent",0));service.act(child,"APPROVE",command("child",0));
        var stop=new ReviewModels.Command("stop",1L,"只停用该母题",null,"STOP_USE");
        assertTrue(service.act(parent,"SOFT_DELETE",stop).affectedQuestionIds().isEmpty());
        assertEquals(List.of(child),mapper.usableIds(List.of(parent,child),service.approvalJson()));
        service.act(parent,"RESTORE",command("restore",2));service.act(parent,"APPROVE",command("again",3));
        var error=new ReviewModels.Command("error",4L,"解答有误",null,"CONTENT_ERROR");
        var receipt=service.act(parent,"RETURN_TO_DRAFT",error);
        assertEquals(List.of(Long.toString(child)),receipt.affectedQuestionIds());
        assertEquals("DRAFT",questions.selectById(child).getStatus());assertEquals(2L,questions.selectById(child).getRowVersion());
        assertEquals(receipt,service.act(parent,"RETURN_TO_DRAFT",error));
        assertTrue(mapper.usableIds(List.of(parent,child),service.approvalJson()).isEmpty());
    }

    @Test void completeContentAndAddedDependenciesInvalidateEvenWithoutAnIllicitVersionBump() {
        long id=seed(),extra=seed();service.act(id,"APPROVE",command("current",0));
        String projection=service.approvalJson();assertEquals(List.of(id),mapper.usableIds(List.of(id),projection));
        jdbc.update("UPDATE kb_question SET answer_html='<p>3</p>' WHERE id=?",id);
        assertTrue(mapper.usableIds(List.of(id),projection).isEmpty());
        jdbc.update("UPDATE kb_question SET answer_html='<p>2</p>' WHERE id=?",id);
        jdbc.update("INSERT INTO kb_question_kp VALUES(?,?)",id,extra);
        assertTrue(mapper.usableIds(List.of(id),projection).isEmpty(),"new unreviewed associations are not invisible to prior facts");
    }

    @Test void singleCalculationNeedsBoundProofAndRestoreNeverRestoresApproval() {
        long id=seed();jdbc.update("UPDATE kb_question SET question_type='CALCULATION' WHERE id=?",id);
        assertThrows(ServiceException.class,()->service.act(id,"APPROVE",command("too-small",0)));
        proofs.grant(new ReviewModels.ExceptionCommand("small-proof","原件就是一道完整母题",id,0L,null,null,null));
        service.act(id,"APPROVE",command("with-proof",0));
        assertTrue(service.readiness(id).eligible());
        assertTrue(service.readiness(id).checks().stream().filter(c->c.code().equals("GRANULARITY")).allMatch(ReviewModels.Check::passed));
        service.act(id,"SOFT_DELETE",new ReviewModels.Command("remove",1L,"暂停使用",null,"STOP_USE"));
        service.act(id,"RESTORE",command("restore-proof",2));
        assertEquals("DRAFT",questions.selectById(id).getStatus());assertFalse(service.readiness(id).canApprove());
        assertThrows(ServiceException.class,()->service.act(id,"APPROVE",command("old-proof",3)));
    }

    @Test void parentFreezeRequiresFullAnswerAndDependencyHashAndRecordsCommittedEvidence() {
        long parent=seed(),child=seed();service.act(parent,"APPROVE",command("freeze-parent",0));
        String hash=service.readiness(parent).contentHash();
        var freeze=service.freezeParent(parent,1L,"stem",hash);
        assertEquals(freeze.contentHash(),service.freezeParent(parent,1L,null,hash).contentHash());
        assertThrows(ServiceException.class,()->service.freezeParent(parent,1L,"wrong-stem",hash));
        jdbc.update("UPDATE kb_question SET answer_html='<p>different answer</p>' WHERE id=?",parent);
        assertThrows(ServiceException.class,()->service.freezeParent(parent,1L,"stem",hash));
        jdbc.update("UPDATE kb_question SET answer_html='<p>2</p>' WHERE id=?",parent);
        jdbc.update("UPDATE kb_question SET creation_kind='DERIVED' WHERE id=?",child);
        jdbc.update("INSERT INTO kb_question_derivation(parent_question_id,child_question_id,is_primary,operation,change_note,preserved_note) VALUES(?,?,true,'CHANGE_VALUES','fixture change','fixture preserve')",parent,child);
        KbQuestion saved=full(child);saved.setReviewParentFreeze(freeze);
        KbQuestion input=new KbQuestion();input.setImportClientKey("derived-key");
        proofs.bindImport(saved,input,"teacher","b".repeat(64));
        assertFalse(proofs.derivedEvidence(full(child)),"orphan file cannot establish committed import");
        jdbc.update("INSERT INTO kb_import_identity VALUES('derived-key','teacher',?,?)",child,"b".repeat(64));
        service.act(child,"APPROVE",command("derive-approved",0));
        service.act(parent,"SOFT_DELETE",new ReviewModels.Command("parent-stop",1L,"只停用母题",null,"STOP_USE"));
        assertTrue(service.readiness(child).eligible());
        assertTrue(proofs.derivedEvidence(full(child)));
        service.act(parent,"RESTORE",command("parent-restore",2));service.act(parent,"APPROVE",command("parent-again",3));
        service.act(parent,"SOFT_DELETE",new ReviewModels.Command("parent-error",4L,"原来源错误",null,"SOURCE_ERROR"));
        assertFalse(proofs.derivedEvidence(full(child)));assertFalse(service.readiness(child).canApprove());
    }

    @Test void corruptedOrOrphanLedgerEvidenceCannotReuseCachedProjection() throws Exception {
        long id=seed();service.act(id,"APPROVE",command("corrupt",0));service.approvalJson();
        Path path=directory.resolve(KbReviewLedger.hash("corrupt".getBytes(java.nio.charset.StandardCharsets.UTF_8))+".prepare.json");
        java.nio.file.Files.writeString(path,"broken");
        assertThrows(ServiceException.class,()->service.approvalJson());
    }

    @Test void realDetailAndReadinessExposeExactlyTheDeriveHashWithoutPerRowListReads() {
        long id=seed();service.act(id,"APPROVE",command("detail",0));
        try(var context=context()) {
            var questionsService=context.getBean(com.ruoyi.knowledge.service.impl.KbQuestionServiceImpl.class);
            KbQuestion detail=questionsService.get(id);
            assertEquals(service.readiness(id).contentHash(),detail.getContentHash());
            assertEquals(detail.getContentHash(),service.freezeParent(id,detail.getRowVersion(),detail.getStemHash(),detail.getContentHash()).contentHash());
            var json=tools.jackson.databind.json.JsonMapper.builder().build();
            assertEquals(detail.getContentHash(),json.readTree(json.writeValueAsString(detail)).get("contentHash").asString());
            KbQuestion injected=json.readValue("{\"id\":\"1\",\"contentHash\":\"forged\"}",KbQuestion.class);
            assertNull(injected.getContentHash());
            KbQuestionQuery query=new KbQuestionQuery();query.setPurpose("NORMAL");query.setIds(List.of(id));
            var rows=questionsService.list(query);
            assertFalse(json.readTree(json.writeValueAsString(rows.get(0))).has("contentHash"));
        }
    }

    @Test void databaseCommitWithoutConfirmationSurvivesLedgerRestart() throws Exception {
        long id=seed();ledger.close();
        ledger=new KbReviewLedger(directory.toString()) {
            boolean fail=true;
            @Override public synchronized void finish(String operationId,String outcome) {
                if(fail) { fail=false; throw new ServiceException("injected confirmation interruption",503); }
                super.finish(operationId,outcome);
            }
        };
        proofs=new KbReviewProofs(ledger,gate,mapper,questions,mock(KbCandidateProxy.class),new KbRecordHtmlConverter(),new KbHtmlCodec());
        service=reviews(manager);
        assertEquals(503,assertThrows(ServiceException.class,()->service.act(id,"APPROVE",command("interrupted-confirm",0))).getCode());
        assertEquals("READY",questions.selectById(id).getStatus());assertNull(ledger.terminal("interrupted-confirm"));
        ledger.close();ledger=new KbReviewLedger(directory.toString());
        proofs=new KbReviewProofs(ledger,gate,mapper,questions,mock(KbCandidateProxy.class),new KbRecordHtmlConverter(),new KbHtmlCodec());
        service=reviews(manager);
        assertEquals("CONFIRMED",service.act(id,"APPROVE",command("interrupted-confirm",0)).outcome());
        assertEquals(1L,questions.selectById(id).getRowVersion());
    }

    private org.springframework.context.annotation.AnnotationConfigApplicationContext context() {
        var context=new org.springframework.context.annotation.AnnotationConfigApplicationContext();
        context.register(ProxyConfiguration.class);
        context.registerBean("transactionManager",PlatformTransactionManager.class,()->manager);
        context.registerBean(KbReviewGate.class,()->gate);
        context.registerBean(KbReviewService.class,()->service);
        context.registerBean(KbReviewMapper.class,()->mapper);
        context.registerBean(com.ruoyi.knowledge.mapper.KbOutlineNodeMapper.class,()->mock(com.ruoyi.knowledge.mapper.KbOutlineNodeMapper.class));
        context.registerBean(KbQualityWriteAspect.class);
        context.registerBean(TestMutation.class,()->new TestMutation(new JdbcTemplate(observed),gate));
        context.registerBean(com.ruoyi.knowledge.service.impl.KbQuestionServiceImpl.class,()->new com.ruoyi.knowledge.service.impl.KbQuestionServiceImpl(
                questions,mock(com.ruoyi.knowledge.mapper.KbKpMapper.class),mock(com.ruoyi.knowledge.service.IKbKpService.class),
                mock(com.ruoyi.knowledge.storage.IKbAssetService.class),session.getMapper(com.ruoyi.knowledge.mapper.KbOccurrenceMapper.class),mock(KnowledgeDictionary.class),service));
        context.refresh();return context;
    }

    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.EnableAspectJAutoProxy(proxyTargetClass=true)
    @org.springframework.transaction.annotation.EnableTransactionManagement(proxyTargetClass=true)
    public static class ProxyConfiguration { }

    public static class TestMutation {
        private final JdbcTemplate jdbc; private final KbReviewGate gate;
        TestMutation(JdbcTemplate jdbc,KbReviewGate gate){this.jdbc=jdbc;this.gate=gate;}
        @KbQualityWrite
        @org.springframework.transaction.annotation.Transactional(propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
        public void importFailure(Long id){
            assertTrue(gate.held());assertTrue(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            jdbc.update("UPDATE kb_question SET status='DRAFT',row_version=row_version+1 WHERE id=?",id);
            throw new IllegalStateException("injected after mutation");
        }
        @KbQualityWrite(dependency="SOURCE")
        @org.springframework.transaction.annotation.Transactional
        public void sourceChange(com.ruoyi.knowledge.domain.KbSource source){
            assertTrue(gate.held());jdbc.update("UPDATE kb_source SET row_version=row_version+1 WHERE id=?",source.getId());
        }
        @KbQualityWrite(dependency="OCCURRENCE")
        @org.springframework.transaction.annotation.Transactional
        public void firstSourceVerification(com.ruoyi.knowledge.domain.KbOccurrence occurrence){
            assertTrue(gate.held());
            assertEquals(1,jdbc.update("UPDATE kb_question_source SET question_no=?,page_from=?,match_status='VERIFIED',matched_stem_hash='stem',evidence_asset_id=?,row_version=row_version+1 WHERE id=? AND row_version=?",
                    occurrence.getQuestionNo(),occurrence.getPageFrom(),occurrence.getEvidenceAssetId(),occurrence.getId(),occurrence.getRowVersion()));
        }
    }

    static class ObservedDataSource extends org.springframework.jdbc.datasource.AbstractDataSource {
        final javax.sql.DataSource delegate; final List<String> sql=new ArrayList<>(); Runnable afterCount;
        ObservedDataSource(javax.sql.DataSource delegate){this.delegate=delegate;}
        public Connection getConnection() throws java.sql.SQLException {return wrap(delegate.getConnection());}
        public Connection getConnection(String user,String password) throws java.sql.SQLException{return wrap(delegate.getConnection(user,password));}
        Connection wrap(Connection connection){return (Connection)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            try {
                Object result=method.invoke(connection,args);
                if(method.getName().equals("prepareStatement") && args[0] instanceof String text){
                    sql.add(text);
                    return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{java.sql.PreparedStatement.class},(statement,operation,parameters)->{
                        try {
                            Object value=operation.invoke(result,parameters);
                            if(afterCount!=null && text.toLowerCase().contains("count(") && (operation.getName().equals("execute")||operation.getName().equals("executeQuery"))){Runnable next=afterCount;afterCount=null;next.run();}
                            return value;
                        } catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
                    });
                }
                return result;
            } catch(java.lang.reflect.InvocationTargetException error){throw error.getCause();}
        });}
    }

    private static ReviewModels.Command command(String id, long version) { return new ReviewModels.Command(id, version, "真实隔离测试审查", true); }
    private KbQuestion full(long id) {
        KbQuestion q=questions.selectById(id); q.setKpIds(questions.selectKpIds(id)); q.setAssetIds(questions.selectAssetIds(id)); return q;
    }
    private ReviewModels.Snapshot snapshot(KbQuestion q) {
        return new ReviewModels.Snapshot(q.getId().toString(),q.getRowVersion().toString(),q.getStatus(),KbQuestionFingerprint.content(q),mapper.facts(q.getId()));
    }
    private static void login() {
        SysUser user = new SysUser(); user.setUserId(42L); user.setUserName("teacher");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(42L,null,user,Set.of("knowledge:question:query","knowledge:question:approve")),null,List.of()));
    }
    private long seed() {
        long id=IDS.incrementAndGet();
        jdbc.update("INSERT INTO kb_question(id,title,stem_html,answer_html,analysis_html,stem_text,stem_hash,status,subject,content_kind,layout_mode,unit_kind,pagination_mode,layout_note,item_count,question_type,difficulty,creation_kind,source_note,row_version) VALUES(?,'fixture','<p>1+1</p>','<p>2</p>','','1+1','stem','DRAFT','MATH','EXERCISE','PLAIN','SINGLE','FLOW','',1,'APPLICATION','MEDIUM','AUTHORED','',0)",id);
        jdbc.update("INSERT INTO kb_kp VALUES(?,'MATH',0)",id);
        jdbc.update("INSERT INTO kb_outline VALUES(?,'MATH','TEXTBOOK','CONFIRMED',0)",id);
        jdbc.update("INSERT INTO kb_outline_node VALUES(?,?,0)",id,id);
        jdbc.update("INSERT INTO kb_question_kp VALUES(?,?)",id,id);
        jdbc.update("INSERT INTO kb_kp_outline VALUES(?,?)",id,id);
        jdbc.update("INSERT INTO kb_outline_node_kp VALUES(?,?)",id,id);
        jdbc.update("INSERT INTO kb_source(id,source_kind,origin_note,row_version) VALUES(?,'AUTHORED','teacher-authored',0)",id);
        jdbc.update("INSERT INTO kb_question_source VALUES(?,?,?,'1',1,'UNVERIFIED',NULL,NULL,0)",id,id,id);
        return id;
    }

    private void createFixture() {
        String[] statements = {
            "CREATE TABLE IF NOT EXISTS kb_question(id BIGINT PRIMARY KEY,title TEXT,stem_html TEXT,answer_html TEXT,analysis_html TEXT,stem_text TEXT,stem_hash VARCHAR(64),status VARCHAR(16),subject VARCHAR(16),content_kind VARCHAR(16),layout_mode VARCHAR(16),unit_kind VARCHAR(16),pagination_mode VARCHAR(32),layout_note TEXT,item_count INT,question_type VARCHAR(32),difficulty VARCHAR(32),creation_kind VARCHAR(16),source_note TEXT,row_version BIGINT,has_image BOOLEAN,create_by VARCHAR(64),create_time DATETIME,update_by VARCHAR(64),update_time DATETIME,remark TEXT) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_kp(id BIGINT PRIMARY KEY,subject VARCHAR(16),row_version BIGINT) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_outline(id BIGINT PRIMARY KEY,subject VARCHAR(16),catalog_kind VARCHAR(16),status VARCHAR(16),row_version BIGINT) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_outline_node(id BIGINT PRIMARY KEY,outline_id BIGINT,row_version BIGINT) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_question_kp(question_id BIGINT,kp_id BIGINT,PRIMARY KEY(question_id,kp_id)) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_kp_outline(kp_id BIGINT,outline_id BIGINT,PRIMARY KEY(kp_id,outline_id)) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_outline_node_kp(node_id BIGINT,kp_id BIGINT,PRIMARY KEY(node_id,kp_id)) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_source(id BIGINT PRIMARY KEY,source_kind VARCHAR(16),origin_note TEXT,row_version BIGINT) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_question_source(id BIGINT PRIMARY KEY,question_id BIGINT,source_id BIGINT,question_no VARCHAR(64),page_from INT,match_status VARCHAR(16),matched_stem_hash VARCHAR(64),evidence_asset_id BIGINT,row_version BIGINT) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_asset(asset_id BIGINT PRIMARY KEY,status VARCHAR(16),sha256 VARCHAR(64),file_size BIGINT) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_question_asset(question_id BIGINT,asset_id BIGINT,PRIMARY KEY(question_id,asset_id)) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_source_asset(source_id BIGINT,asset_id BIGINT,role VARCHAR(16),PRIMARY KEY(source_id,asset_id,role)) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_question_derivation(parent_question_id BIGINT,child_question_id BIGINT,is_primary BOOLEAN,PRIMARY KEY(parent_question_id,child_question_id)) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_material_item(question_id BIGINT) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_product_question(question_id BIGINT) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_verified_exam_source(source_id BIGINT,authenticity_status VARCHAR(16)) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_occurrence_collection(occurrence_id BIGINT,collection_kind VARCHAR(32)) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS kb_import_identity(client_key VARCHAR(160),create_by VARCHAR(64),question_id BIGINT,request_fingerprint VARCHAR(64)) ENGINE=InnoDB"
        };
        for (String statement : statements) jdbc.execute(statement);
        Integer hasTitle=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='prd010_review_test' AND table_name='kb_source' AND column_name='title'",Integer.class);
        if(hasTitle==0)jdbc.execute("ALTER TABLE kb_source ADD COLUMN title VARCHAR(300)");
        for(String column:List.of("operation","change_note","preserved_note")) {
            Integer exists=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='prd010_review_test' AND table_name='kb_question_derivation' AND column_name=?",Integer.class,column);
            if(exists==0)jdbc.execute("ALTER TABLE kb_question_derivation ADD COLUMN "+column+" VARCHAR(300)");
        }
    }
}

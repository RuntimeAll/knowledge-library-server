package com.ruoyi.knowledge.review;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.knowledge.importing.mapper.KbImportMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class KbCandidateProxyTest {
    @TempDir Path directory;
    HttpServer server;
    JSONObject candidate;
    AtomicReference<String> response=new AtomicReference<>();
    AtomicReference<String> actualUser=new AtomicReference<>();
    AtomicReference<String> actualId=new AtomicReference<>();
    KbImportMapper imports;
    @BeforeEach void setup() throws Exception {
        candidate=JSON.parseObject(getClass().getResourceAsStream("/review/pipeline-candidate-hash-vector.json").readAllBytes());
        imports=mock(KbImportMapper.class);when(imports.selectInstanceId()).thenReturn("instance-example");
        Files.writeString(directory.resolve("token"),"test-only-token");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/pipeline/runs/run-example/candidates/candidate-example",exchange->{
            actualUser.set(exchange.getRequestHeaders().getFirst("X-Knowledge-Username"));
            actualId.set(exchange.getRequestHeaders().getFirst("X-Knowledge-User-Id"));
            byte[] bytes=response.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);
            try(var body=exchange.getResponseBody()){body.write(bytes);}
        });server.start();publish();login(42L,"teacher-example");
    }
    @AfterEach void close(){if(server!=null)server.stop(0);SecurityContextHolder.clearContext();}
    void publish(){response.set(JSON.toJSONString(JSONObject.of("code","OK","data",candidate),com.alibaba.fastjson2.JSONWriter.Feature.WriteNulls));}
    KbCandidateProxy proxy(String writer,String id,String owner){return new KbCandidateProxy("http://127.0.0.1:"+server.getAddress().getPort(),directory.resolve("token").toString(),imports,writer,id,owner);}
    @Test void pythonCanonicalVectorMatchesRawUtf8HashAndActualIdentity() {
        var value=proxy("","","").get("run-example","candidate-example");
        assertEquals("55f1a92fd3a4ac7d86ba35e2ce41eac4e5de581b19d2590ca237f1a5c30ebc5e",value.candidateHash());
        assertEquals("teacher-example",actualUser.get());assertEquals("42",actualId.get());
        assertEquals(List.of(1L),value.record().metadata().kpIds());
    }
    @Test void displayedMutationCannotHideBehindOriginalCanonicalHash() {
        candidate.getJSONObject("record").put("answer","changed");publish();
        assertThrows(ServiceException.class,()->proxy("","","").get("run-example","candidate-example"));
    }
    @Test void duplicateJsonAndUnknownRecordFieldsFailClosed() {
        response.set("{\"code\":\"OK\",\"code\":\"OK\",\"data\":"+candidate+"}");
        assertThrows(ServiceException.class,()->proxy("","","").get("run-example","candidate-example"));
        candidate.getJSONObject("record").put("allowSingle",true);publish();
        assertThrows(ServiceException.class,()->proxy("","","").get("run-example","candidate-example"));
    }
    @Test void delegationRequiresExactRealUserIdAndNameAndFixedOwner() {
        login(77L,"kb-pipeline");
        var allowed=proxy("kb-pipeline","77","teacher-example").get("run-example","candidate-example");
        assertEquals("teacher-example",allowed.ownerUsername());assertEquals("kb-pipeline",actualUser.get());assertEquals("77",actualId.get());
        assertThrows(ServiceException.class,()->proxy("kb-pipeline","78","teacher-example").get("run-example","candidate-example"));
        assertThrows(ServiceException.class,()->proxy("kb-pipeline","77","other-owner").get("run-example","candidate-example"));
        login(77L,"other-user");assertThrows(ServiceException.class,()->proxy("kb-pipeline","77","teacher-example").get("run-example","candidate-example"));
    }
    static void login(Long id,String name){SysUser user=new SysUser();user.setUserId(id);user.setUserName(name);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(id,null,user,Set.of()),null,List.of()));}
}

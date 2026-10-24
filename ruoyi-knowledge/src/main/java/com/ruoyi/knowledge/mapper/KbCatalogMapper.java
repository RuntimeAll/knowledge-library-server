package com.ruoyi.knowledge.mapper;

import com.ruoyi.knowledge.catalog.*;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/** Typed catalog persistence; owner discriminators select only the two explicit table families. */
public interface KbCatalogMapper {
    List<KbCatalogBatch> batchProfiles(@Param("ownerKind") String kind, @Param("ownerIds") List<Long> ids);
    List<KbCatalogBatch> batchScopes(@Param("ownerKind") String kind, @Param("ownerIds") List<Long> ids);
    List<KbCatalogBatch> batchKps(@Param("ownerKind") String kind, @Param("ownerIds") List<Long> ids);
    List<KbCatalogBatch> batchRegions(@Param("ownerKind") String kind, @Param("ownerIds") List<Long> ids);
    List<KbCatalogBatch> batchExams(@Param("ownerIds") List<Long> ids);
    List<KbCatalogBatch> batchProductQuestions(@Param("ownerIds") List<Long> ids);
    List<KbCatalogBatch> batchProductSources(@Param("ownerIds") List<Long> ids);
    List<KbCatalogBatch> batchCollections(@Param("ownerIds") List<Long> ids);

    KbCatalogProfile profile(@Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    int saveProfile(
            @Param("ownerKind") String ownerKind,
            @Param("ownerId") Long ownerId,
            @Param("profile") KbCatalogProfile profile,
            @Param("actor") String actor);

    List<KbOutlineScope> scopes(
            @Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    List<Long> scopeNodes(@Param("ownerKind") String ownerKind, @Param("scopeId") Long scopeId);

    int insertScope(
            @Param("ownerKind") String ownerKind,
            @Param("ownerId") Long ownerId,
            @Param("scope") KbOutlineScope scope,
            @Param("actor") String actor);

    int insertScopeNode(
            @Param("ownerKind") String ownerKind,
            @Param("scopeId") Long scopeId,
            @Param("nodeId") Long nodeId);

    int deleteScopeNodes(@Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    int deleteScopes(@Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    List<Long> declaredKps(@Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    List<Long> actualKps(@Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    int insertKp(
            @Param("ownerKind") String ownerKind,
            @Param("ownerId") Long ownerId,
            @Param("kpId") Long kpId);

    int deleteKps(@Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    List<KbRegionLink> regions(
            @Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    int insertRegion(
            @Param("ownerKind") String ownerKind,
            @Param("ownerId") Long ownerId,
            @Param("region") KbRegionLink region);

    int deleteRegions(@Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    int deleteProfile(@Param("ownerKind") String ownerKind, @Param("ownerId") Long ownerId);

    KbRegion region(String code);

    List<KbRegion> listRegions(
            @Param("parentCode") String parentCode, @Param("keyword") String keyword);

    List<String> descendantRegions(String code);

    KbExamDetail exam(Long sourceId);

    int saveExamDetail(
            @Param("sourceId") Long sourceId,
            @Param("exam") KbExamDetail exam,
            @Param("actor") String actor);

    int deleteExamDetail(Long sourceId);

    List<KbProductQuestionRef> productQuestions(Long productId);

    List<KbProductReferenceSource> productSources(Long productId);

    int insertProductQuestion(
            @Param("productId") Long productId, @Param("ref") KbProductQuestionRef ref);

    int insertProductSource(
            @Param("productId") Long productId, @Param("ref") KbProductReferenceSource ref);

    int deleteProductQuestions(Long productId);

    int deleteProductSources(Long productId);

    List<KbOccurrenceCollection> collections(Long occurrenceId);

    int deleteCollections(Long occurrenceId);

    int insertCollection(
            @Param("occurrenceId") Long occurrenceId,
            @Param("item") KbOccurrenceCollection item,
            @Param("actor") String actor,
            @Param("version") Long version);

    long countSourceEvidence(@Param("sourceId") Long sourceId, @Param("assetId") Long assetId);
}

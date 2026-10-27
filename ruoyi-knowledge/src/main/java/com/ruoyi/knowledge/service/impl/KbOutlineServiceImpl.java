package com.ruoyi.knowledge.service.impl;

import static com.ruoyi.knowledge.service.impl.KnowledgeRules.changed;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.found;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.ids;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.text;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.unreferenced;
import static com.ruoyi.knowledge.service.impl.KnowledgeRules.version;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.knowledge.domain.KbOutline;
import com.ruoyi.knowledge.domain.KbOutlineNode;
import com.ruoyi.knowledge.dto.KbOutlineNodeKp;
import com.ruoyi.knowledge.mapper.KbKpMapper;
import com.ruoyi.knowledge.mapper.KbOutlineMapper;
import com.ruoyi.knowledge.mapper.KbOutlineNodeMapper;
import com.ruoyi.knowledge.service.IKbOutlineService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Set;

@Service
public class KbOutlineServiceImpl implements IKbOutlineService {
    private final KbOutlineMapper mapper;
    private final KbOutlineNodeMapper nodes;
    private final KbKpMapper points;
    private final KnowledgeDictionary dictionary;

    public KbOutlineServiceImpl(
            KbOutlineMapper mapper, KbOutlineNodeMapper nodes, KbKpMapper points, KnowledgeDictionary dictionary) {
        this.mapper = mapper;
        this.nodes = nodes;
        this.points = points;
        this.dictionary = dictionary;
    }

    public List<KbOutline> list(KbOutline query) {
        return mapper.selectList(query);
    }

    public com.ruoyi.knowledge.domain.dto.KbOutlineOptions options() {
        return TextbookOptions.options(mapper.selectTextbookVersions());
    }

    public KbOutline get(Long id) {
        KbOutline data = found(mapper.selectById(id));
        KbOutlineNode query = new KbOutlineNode();
        query.setOutlineId(id);
        List<KbOutlineNode> values = nodes.selectList(query);
        if (!values.isEmpty()) {
            List<Long> nodeIds = values.stream().map(KbOutlineNode::getId).toList();
            Map<Long, List<Long>> kpIds = nodes.selectKpsForNodes(nodeIds).stream()
                    .collect(Collectors.groupingBy(KbOutlineNodeKp::getNodeId,
                            Collectors.mapping(KbOutlineNodeKp::getKpId, Collectors.toList())));
            values.forEach(n -> n.setKpIds(kpIds.getOrDefault(n.getId(), List.of())));
        }
        data.setNodes(values);
        return data;
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "NONE")
    @Transactional
    public KbOutline create(KbOutline data) {
        data.setId(null);
        validate(data);
        mapper.insert(data);
        return get(data.getId());
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "OUTLINE")
    @Transactional
    public KbOutline update(KbOutline data) {
        points.lockGraph();
        KbOutline current = found(mapper.lockById(data.getId()));
        version(data.getRowVersion(), current.getRowVersion());
        data.setTextbookVersion(TextbookOptions.normalizeVersion(data.getTextbookVersion()));
        if (catalogIdentityChanged(current, data))
            unreferenced(mapper.countCatalogReferences(data.getId()), "教材已被来源或成品引用，不能改变教材身份");
        if ((points.countOutlinePoints(data.getId()) > 0 || mapper.countNodes(data.getId()) > 0)
                && (!Objects.equals(current.getSubject(), data.getSubject())
                    || !Objects.equals(current.getCatalogKind(), data.getCatalogKind())))
            throw new ServiceException("大纲已有考点，不能改变科目或目录类型", 409);
        if ("CONFIRMED".equals(current.getStatus()) && textbookIdentityChanged(current, data))
            data.setStatus("DRAFT");
        validate(data);
        changed(mapper.update(data));
        return get(data.getId());
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "NONE")
    @Transactional
    public void delete(Long[] values) {
        points.lockGraph();
        for (Long id : values) {
            found(mapper.lockById(id));
            unreferenced(mapper.countCatalogReferences(id), "教材大纲仍被来源或成品引用");
            unreferenced(points.countOutlinePoints(id), "教材大纲仍挂有考点");
            unreferenced(mapper.countNodes(id), "大纲仍有章节，请先清空章节");
            mapper.deleteById(id);
        }
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "OUTLINE")
    @Transactional
    public KbOutlineNode createNode(KbOutlineNode data) {
        points.lockGraph();
        KbOutline outline = found(mapper.lockById(data.getOutlineId()));
        validateNode(data);
        nodes.insert(data);
        saveKps(data);
        invalidateConfirmation(outline);
        return node(data.getId());
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "OUTLINE")
    @Transactional
    public KbOutlineNode updateNode(KbOutlineNode data) {
        // Serialize the tree before any consistent read creates an RR snapshot.
        points.lockGraph();
        KbOutline outline = found(mapper.lockById(data.getOutlineId()));
        KbOutlineNode old = found(nodes.lockById(data.getId()));
        if (!Objects.equals(old.getOutlineId(), data.getOutlineId()))
            throw new ServiceException("不能跨大纲移动章节", 400);
        version(data.getRowVersion(), old.getRowVersion());
        if (!Objects.equals(old.getParentId(), data.getParentId()))
            unreferenced(mapper.countNodeCatalogReferences(data.getId()), "章节已被来源或成品引用，不能改变层级位置");
        validateNode(data);
        changed(nodes.update(data));
        saveKps(data);
        if (!Objects.equals(old.getTitle(), data.getTitle())
                || !Objects.equals(old.getParentId(), data.getParentId())
                || !Objects.equals(old.getSortOrder(), data.getSortOrder()))
            invalidateConfirmation(outline);
        return node(data.getId());
    }

    @com.ruoyi.knowledge.review.KbQualityWrite(dependency = "NODE")
    @Transactional
    public void deleteNode(Long id) {
        points.lockGraph();
        KbOutlineNode data = found(nodes.selectById(id));
        KbOutline outline = found(mapper.lockById(data.getOutlineId()));
        unreferenced(nodes.countChildren(id), "章节仍有子章节");
        unreferenced(mapper.countNodeCatalogReferences(id), "章节仍被来源或成品引用");
        if (!nodes.selectKpIds(id).isEmpty()) throw new ServiceException("章节仍挂有考点，请先解除", 409);
        nodes.deleteById(id);
        invalidateConfirmation(outline);
    }

    private KbOutlineNode node(Long id) {
        KbOutlineNode data = found(nodes.selectById(id));
        data.setKpIds(nodes.selectKpIds(id));
        return data;
    }

    private void validateNode(KbOutlineNode data) {
        data.setTitle(text(data.getTitle(), "章节标题", 200));
        if (data.getParentId() != null && data.getParentId() == 0) data.setParentId(null);
        Long ancestor = data.getParentId();
        Set<Long> seen = new HashSet<>();
        while (ancestor != null) {
            if (ancestor.equals(data.getId()) || !seen.add(ancestor))
                throw new ServiceException("章节关系不能形成循环", 409);
            KbOutlineNode parent = found(nodes.selectById(ancestor));
            if (!Objects.equals(parent.getOutlineId(), data.getOutlineId()))
                throw new ServiceException("父章节须属于同一大纲", 400);
            ancestor = parent.getParentId();
        }
        if (data.getSortOrder() == null) data.setSortOrder(0);
    }

    private void validate(KbOutline data) {
        data.setTitle(text(data.getTitle(), "大纲标题", 200));
        data.setCatalogKind(KnowledgeRules.choice(data.getCatalogKind(), "TEXTBOOK", "TEXTBOOK", "MATERIAL_COVERAGE"));
        data.setStatus(KnowledgeRules.choice(data.getStatus(), "DRAFT", "DRAFT", "CONFIRMED", "RETIRED"));
        dictionary.validate("kb_outline_kind", data.getCatalogKind(), "目录类型");
        dictionary.validate("kb_outline_status", data.getStatus(), "目录状态");
        dictionary.validate("kb_subject", data.getSubject(), "科目");
        data.setTextbookVersion(TextbookOptions.normalizeVersion(data.getTextbookVersion()));
        TextbookOptions.validate(data.getGrade(), data.getTerm());
        if ("TEXTBOOK".equals(data.getCatalogKind())) {
            text(data.getSubject(), "科目", 30);
            text(data.getTextbookVersion(), "教材版本", 40);
            text(data.getGrade(), "年级", 20);
            text(data.getTerm(), "学期", 20);
            if ("CONFIRMED".equals(data.getStatus())) {
                text(data.getEdition(), "修订年份或ISBN", 100);
                text(data.getCatalogSource(), "教材目录核对依据", 1000);
                if (data.getId() == null || mapper.countNodes(data.getId()) == 0)
                    throw new ServiceException("教材目录尚无章节，不能确认", 400);
            }
        }
        if (data.getEdition() != null && data.getEdition().length() > 100
                || data.getCatalogSource() != null && data.getCatalogSource().length() > 1000)
            throw new ServiceException("教材版本说明或核对依据过长", 400);
    }

    private void saveKps(KbOutlineNode data) {
        if (data.getKpIds() == null) return;
        List<Long> wanted = ids(data.getKpIds());
        KbOutline outline = found(mapper.selectById(data.getOutlineId()));
        for (Long id : wanted) {
            var kp = found(points.lockById(id));
            if ("TEXTBOOK".equals(outline.getCatalogKind())) {
                if (!Objects.equals(outline.getSubject(), kp.getSubject()))
                    throw new ServiceException("章节和考点的科目必须一致", 400);
                points.insertOutline(id, outline.getId());
            }
        }
        nodes.deleteKps(data.getId());
        for (Long id : wanted) nodes.insertKp(data.getId(), id);
    }

    private void invalidateConfirmation(KbOutline outline) {
        if ("CONFIRMED".equals(outline.getStatus())) {
            outline.setStatus("DRAFT");
            changed(mapper.update(outline));
        }
    }

    private boolean textbookIdentityChanged(KbOutline old, KbOutline updated) {
        return !Objects.equals(old.getTextbookVersion(), updated.getTextbookVersion())
                || !Objects.equals(old.getGrade(), updated.getGrade())
                || !Objects.equals(old.getTerm(), updated.getTerm())
                || !Objects.equals(old.getEdition(), updated.getEdition())
                || !Objects.equals(old.getCatalogSource(), updated.getCatalogSource())
                || !Objects.equals(old.getSubject(), updated.getSubject())
                || !Objects.equals(old.getCatalogKind(), updated.getCatalogKind());
    }

    private boolean catalogIdentityChanged(KbOutline old, KbOutline updated) {
        return !Objects.equals(old.getTextbookVersion(), updated.getTextbookVersion())
                || !Objects.equals(old.getGrade(), updated.getGrade())
                || !Objects.equals(old.getTerm(), updated.getTerm())
                || !Objects.equals(old.getEdition(), updated.getEdition())
                || !Objects.equals(old.getSubject(), updated.getSubject())
                || !Objects.equals(old.getCatalogKind(), updated.getCatalogKind());
    }
}

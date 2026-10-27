package com.ruoyi.web.controller.knowledge;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.knowledge.review.KbCandidateProxy;
import com.ruoyi.knowledge.review.KbReviewProofs;
import com.ruoyi.web.core.docs.CoreApi;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Current-user proxy for immutable candidate evidence, never a credential forwarding endpoint. */
@RestController
@RequestMapping("/knowledge/review")
public class KbCandidateController extends BaseController {
    private final KbReviewProofs proofs;
    public KbCandidateController(KbReviewProofs proofs) { this.proofs = proofs; }

    @GetMapping("/runs/{runId}/candidates/{candidateKey}")
    @PreAuthorize("@ss.hasAnyPermi('knowledge:question:query,knowledge:question:granularityException')")
    @CoreApi(value = "review.getCandidate", response = KbCandidateProxy.Candidate.class, published = false)
    public AjaxResult candidate(@PathVariable String runId, @PathVariable String candidateKey) {
        return success(proofs.candidate(runId, candidateKey));
    }
}

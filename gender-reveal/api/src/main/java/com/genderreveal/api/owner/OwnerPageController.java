package com.genderreveal.api.owner;

import com.genderreveal.api.auth.OwnerPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/pages")
public class OwnerPageController {

    private final OwnerPageService ownerPageService;

    public OwnerPageController(OwnerPageService ownerPageService) {
        this.ownerPageService = ownerPageService;
    }

    @GetMapping
    public ResponseEntity<List<OwnerPageSummary>> list(OwnerPrincipal owner) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ownerPageService.list(owner.email()));
    }

    @GetMapping("/{slug}/stats")
    public ResponseEntity<PageStats> stats(OwnerPrincipal owner, @PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ownerPageService.stats(slug, owner.email()));
    }

    @PostMapping("/{slug}/extend")
    public ResponseEntity<OwnerPageSummary> extend(OwnerPrincipal owner, @PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ownerPageService.extend(slug, owner.email()));
    }
}

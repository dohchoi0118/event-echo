package com.genderreveal.api.page;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/pages")
public class PageController {

    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
    }

    @PostMapping
    public ResponseEntity<PageCreateResponse> create(@Valid @RequestBody PageCreateRequest request) {
        Page page = pageService.create(request);
        PageCreateResponse response = PageCreateResponse.from(page);
        return ResponseEntity.created(URI.create("/api/pages/" + page.getSlug())).body(response);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PagePublicResponse> getBySlug(@PathVariable String slug) {
        PagePublicResponse response = pageService.getPublicView(slug);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}

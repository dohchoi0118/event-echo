package com.genderreveal.api.owner;

import com.genderreveal.api.auth.OwnerPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/owner/pages/{slug}/guestbook")
public class OwnerGuestbookController {

    private final OwnerGuestbookService ownerGuestbookService;

    public OwnerGuestbookController(OwnerGuestbookService ownerGuestbookService) {
        this.ownerGuestbookService = ownerGuestbookService;
    }

    @GetMapping
    public ResponseEntity<List<OwnerGuestbookEntryResponse>> list(OwnerPrincipal owner, @PathVariable String slug) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ownerGuestbookService.list(slug, owner.email()));
    }

    @PatchMapping("/{entryId}")
    public ResponseEntity<Void> setHidden(OwnerPrincipal owner, @PathVariable String slug,
                                           @PathVariable Long entryId,
                                           @Valid @RequestBody GuestbookHiddenRequest request) {
        ownerGuestbookService.setHidden(slug, owner.email(), entryId, request.hidden());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(OwnerPrincipal owner, @PathVariable String slug, @PathVariable Long entryId) {
        ownerGuestbookService.delete(slug, owner.email(), entryId);
        return ResponseEntity.noContent().build();
    }
}

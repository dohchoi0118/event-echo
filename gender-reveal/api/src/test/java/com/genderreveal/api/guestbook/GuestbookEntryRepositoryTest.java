package com.genderreveal.api.guestbook;

import com.genderreveal.api.page.Page;
import com.genderreveal.api.page.PageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class GuestbookEntryRepositoryTest {

    @Autowired
    private GuestbookEntryRepository guestbookEntryRepository;

    @Autowired
    private PageRepository pageRepository;

    @Test
    void listsVisibleEntriesNewestFirstAndExcludesHidden() {
        Page page = pageRepository.save(samplePage());

        Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS);
        GuestbookEntry older = guestbookEntryRepository.save(
            new GuestbookEntry(page.getId(), "이모", "축하해요", t1));
        GuestbookEntry newer = guestbookEntryRepository.save(
            new GuestbookEntry(page.getId(), "삼촌", "고생하셨어요", t2));
        GuestbookEntry hidden = guestbookEntryRepository.save(
            new GuestbookEntry(page.getId(), "스팸", "광고", Instant.now()));
        hidden.hide();
        guestbookEntryRepository.save(hidden);

        List<GuestbookEntry> visible = guestbookEntryRepository
            .findByPageIdAndHiddenFalseOrderByCreatedAtDesc(page.getId());

        assertThat(visible).extracting(GuestbookEntry::getNickname)
            .containsExactly("삼촌", "이모");
    }

    @Test
    void ordersByTrueChronologicalOrderAcrossMixedSubSecondPrecision() {
        Page page = pageRepository.save(samplePage());

        // Instant.toString() renders these with a DIFFERENT number of fractional digits
        // (zero vs three) — a purely lexicographic string comparison would sort the
        // whole-second timestamp as "newest" among same-second entries, which is wrong.
        Instant wholeSecond = Instant.parse("2026-01-01T00:00:00Z");
        Instant halfSecondLater = Instant.parse("2026-01-01T00:00:00.500Z");

        guestbookEntryRepository.save(new GuestbookEntry(page.getId(), "먼저", "먼저 왔어요", wholeSecond));
        guestbookEntryRepository.save(new GuestbookEntry(page.getId(), "나중", "나중에 왔어요", halfSecondLater));

        List<GuestbookEntry> visible = guestbookEntryRepository
            .findByPageIdAndHiddenFalseOrderByCreatedAtDesc(page.getId());

        assertThat(visible).extracting(GuestbookEntry::getNickname)
            .containsExactly("나중", "먼저");
    }

    private Page samplePage() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return new Page(
            "guestbook-test-" + System.nanoTime(), "뽀튼이", "boy", now.minus(1, ChronoUnit.HOURS),
            null, "메시지", "box", false, "owner@example.com", now, now.plus(30, ChronoUnit.DAYS)
        );
    }
}

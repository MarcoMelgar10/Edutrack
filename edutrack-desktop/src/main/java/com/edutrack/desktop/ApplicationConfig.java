package com.edutrack.desktop;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.attachment.AttachmentService;
import com.edutrack.application.attachment.AttachmentStorage;
import com.edutrack.application.backup.BackupArchive;
import com.edutrack.application.backup.BackupService;
import com.edutrack.application.evidence.EvidenceService;
import com.edutrack.application.export.ExportService;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.insights.InsightsService;
import com.edutrack.application.journal.JournalService;
import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.practice.PracticeService;
import com.edutrack.application.progress.ConsistencyService;
import com.edutrack.application.progress.PaceService;
import com.edutrack.application.progress.StudyDaySource;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.review.ReviewService;
import com.edutrack.application.review.ReviewStudyDays;
import com.edutrack.application.search.SearchIndex;
import com.edutrack.application.search.SearchService;
import com.edutrack.application.session.SessionService;
import com.edutrack.application.skill.CompetencyService;
import com.edutrack.application.template.TemplateCatalog;
import com.edutrack.application.template.TemplateService;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.attachment.AttachmentRepository;
import com.edutrack.domain.evidence.EvidenceRepository;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.journal.JournalEntryRepository;
import com.edutrack.domain.measure.MeasureRepository;
import com.edutrack.domain.practice.PracticeRoutineRepository;
import com.edutrack.domain.record.LearningRecordRepository;
import com.edutrack.domain.review.CardRepository;
import com.edutrack.domain.review.ReviewLogRepository;
import com.edutrack.domain.session.StudySessionRepository;
import com.edutrack.domain.skill.CompetencyRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Wires the framework-free application services. The repository ports are implemented by the
 * infrastructure adapters, which Spring finds by component scanning.
 */
@Configuration(proxyBeanMethods = false)
public class ApplicationConfig {

    /** Ticks in microseconds: PostgreSQL {@code timestamptz} precision, so stored instants round-trip exactly. */
    @Bean
    Clock clock() {
        return Clock.tick(Clock.systemDefaultZone(), Duration.of(1, ChronoUnit.MICROS));
    }

    @Bean
    AreaService areaService(AreaRepository areas, GoalRepository goals, Clock clock) {
        return new AreaService(areas, goals, clock);
    }

    @Bean
    GoalService goalService(GoalRepository goals, AreaRepository areas, LearningRecordRepository records,
                            Clock clock) {
        return new GoalService(goals, areas, records, clock);
    }

    @Bean
    LearningRecordService learningRecordService(LearningRecordRepository records, GoalRepository goals,
                                                MeasureRepository measures, Clock clock) {
        return new LearningRecordService(records, goals, measures, clock);
    }

    @Bean
    CompetencyService competencyService(CompetencyRepository competencies, GoalRepository goals, Clock clock) {
        return new CompetencyService(competencies, goals, clock);
    }

    @Bean
    MeasureService measureService(MeasureRepository measures, LearningRecordRepository records,
                                  CompetencyRepository competencies, GoalRepository goals, Clock clock) {
        return new MeasureService(measures, records, competencies, goals, clock);
    }

    @Bean
    ReviewService reviewService(CardRepository cards, ReviewLogRepository logs, GoalRepository goals,
                                AreaRepository areas, Clock clock) {
        return new ReviewService(cards, logs, goals, areas, clock);
    }

    /** Days with at least one answered card count as study days. */
    @Bean
    StudyDaySource reviewStudyDays(ReviewLogRepository logs, Clock clock) {
        return new ReviewStudyDays(logs, clock);
    }

    @Bean
    AttachmentService attachmentService(AttachmentRepository attachments, AttachmentStorage storage, Clock clock) {
        return new AttachmentService(attachments, storage, clock);
    }

    @Bean
    EvidenceService evidenceService(EvidenceRepository evidence, AttachmentService attachments,
                                    GoalRepository goals, Clock clock) {
        return new EvidenceService(evidence, attachments, goals, clock);
    }

    @Bean
    SessionService sessionService(StudySessionRepository sessions, GoalRepository goals, Clock clock) {
        return new SessionService(sessions, goals, clock);
    }

    @Bean
    PracticeService practiceService(PracticeRoutineRepository routines, StudySessionRepository sessions,
                                    GoalRepository goals, Clock clock) {
        return new PracticeService(routines, sessions, goals, clock);
    }

    @Bean
    JournalService journalService(JournalEntryRepository entries, StudySessionRepository sessions,
                                  GoalRepository goals, Clock clock) {
        return new JournalService(entries, sessions, goals, clock);
    }

    @Bean
    PaceService paceService(GoalRepository goals, StudySessionRepository sessions, Clock clock) {
        return new PaceService(goals, sessions, clock);
    }

    @Bean
    InsightsService insightsService(GoalRepository goals, AreaRepository areas, StudySessionRepository sessions,
                                    Clock clock) {
        return new InsightsService(goals, areas, sessions, clock);
    }

    /** A day counts as a study day when it has a session, a card review or a check-in. */
    @Bean
    ConsistencyService consistencyService(SessionService sessionService, JournalService journalService,
                                          StudyDaySource reviewStudyDays, Clock clock) {
        return new ConsistencyService(List.of(sessionService.asStudyDaySource(), reviewStudyDays,
                journalService.asStudyDaySource()), clock);
    }

    @Bean
    SearchService searchService(SearchIndex index) {
        return new SearchService(index);
    }

    @Bean
    BackupService backupService(BackupArchive archive, Clock clock) {
        return new BackupService(archive, clock);
    }

    @Bean
    ExportService exportService(GoalService goals, LearningRecordService records, ReviewService reviews,
                                MeasureService measures) {
        return new ExportService(goals, records, reviews, measures);
    }

    @Bean
    TemplateService templateService(TemplateCatalog catalog, GoalService goals, CompetencyService competencies,
                                    MeasureService measures, PracticeService practice, ReviewService reviews,
                                    Clock clock) {
        return new TemplateService(catalog, goals, competencies, measures, practice, reviews, clock);
    }
}

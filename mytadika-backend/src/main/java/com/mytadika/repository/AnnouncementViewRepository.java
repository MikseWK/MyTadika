package com.mytadika.repository;
import com.mytadika.model.AnnouncementView;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnnouncementViewRepository extends JpaRepository<AnnouncementView, Long> {
    int countByAnnouncementId(Long announcementId);
    boolean existsByAnnouncementIdAndViewerAccountId(Long announcementId, String viewerAccountId);
    void deleteByAnnouncementId(Long announcementId);
    List<AnnouncementView> findByAnnouncementIdIn(List<Long> announcementIds);
    List<AnnouncementView> findByAnnouncementId(Long announcementId);
}

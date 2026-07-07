package com.mytadika.repository;
import com.mytadika.model.ClassMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface ClassMemberRepository extends JpaRepository<ClassMember, Long> {
    List<ClassMember> findByAccountId(String accountId);
    List<ClassMember> findByClassroomId(Long classroomId);
    List<ClassMember> findByClassroomIdAndRole(Long classroomId, String role);
    boolean existsByClassroomIdAndAccountId(Long classroomId, String accountId);

    @Modifying
    @Query("DELETE FROM ClassMember m WHERE m.classroomId = :classroomId AND m.accountId = :accountId")
    void deleteByClassroomIdAndAccountId(@Param("classroomId") Long classroomId, @Param("accountId") String accountId);

    @Modifying
    @Query("DELETE FROM ClassMember m WHERE m.classroomId = :classroomId")
    void deleteByClassroomId(@Param("classroomId") Long classroomId);
}

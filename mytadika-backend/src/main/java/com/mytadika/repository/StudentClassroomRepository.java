package com.mytadika.repository;

import com.mytadika.model.StudentClassroom;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StudentClassroomRepository extends JpaRepository<StudentClassroom, Long> {
    List<StudentClassroom> findByStudentId(Long studentId);
    List<StudentClassroom> findByStudentIdIn(List<Long> studentIds);
    List<StudentClassroom> findByClassroomId(Long classroomId);
    List<StudentClassroom> findByClassroomIdIn(List<Long> classroomIds);
    boolean existsByStudentIdAndClassroomId(Long studentId, Long classroomId);
    void deleteByClassroomId(Long classroomId);
    void deleteByStudentIdAndClassroomId(Long studentId, Long classroomId);
    int countByClassroomId(Long classroomId);
}

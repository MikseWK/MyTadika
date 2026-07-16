package com.mytadika.repository;

import com.mytadika.model.AllergyProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AllergyProfileRepository extends JpaRepository<AllergyProfile, Long> {
}

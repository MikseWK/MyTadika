package com.mytadika.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixes schema inconsistencies that can't be handled by JPA auto-ddl.
 * Runs before other ApplicationRunners (Order 0 vs default Order ~Integer.MAX_VALUE).
 */
@Component
@Order(0)
public class DbMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    public DbMigrationRunner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Add topic column to announcements if it doesn't exist yet
        try {
            Integer topicColExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_name='announcements' AND column_name='topic'",
                Integer.class);
            if (topicColExists != null && topicColExists == 0) {
                jdbc.execute("ALTER TABLE announcements ADD COLUMN topic VARCHAR(100)");
                System.out.println("[DbMigrationRunner] Added 'topic' column to announcements table.");
            }
        } catch (Exception e) {
            System.err.println("[DbMigrationRunner] Could not add topic column: " + e.getMessage());
        }

        // Create student_classrooms join table and migrate existing data
        try {
            Integer tableExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='student_classrooms'",
                Integer.class);
            if (tableExists != null && tableExists == 0) {
                jdbc.execute(
                    "CREATE TABLE student_classrooms (" +
                    "  id bigserial PRIMARY KEY," +
                    "  student_id bigint NOT NULL," +
                    "  classroom_id bigint NOT NULL," +
                    "  joined_at timestamptz DEFAULT now()," +
                    "  UNIQUE(student_id, classroom_id)" +
                    ")");
                // Migrate existing student.classroom_id values
                jdbc.execute(
                    "INSERT INTO student_classrooms (student_id, classroom_id) " +
                    "SELECT id, classroom_id FROM student " +
                    "WHERE classroom_id IS NOT NULL " +
                    "ON CONFLICT DO NOTHING");
                System.out.println("[DbMigrationRunner] Created student_classrooms table and migrated existing data.");
            }
        } catch (Exception e) {
            System.err.println("[DbMigrationRunner] Could not create student_classrooms table: " + e.getMessage());
        }

        // Fix FK constraint: student.classroom_id was pointing to "classroom" (singular)
        // but the active table is "classrooms" (plural).
        try {
            // Check if the FK currently points to the wrong table
            Integer wrongFkCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.referential_constraints rc " +
                "JOIN information_schema.table_constraints tc ON rc.constraint_name = tc.constraint_name " +
                "WHERE rc.constraint_name = 'student_classroom_id_fkey' " +
                "AND rc.unique_constraint_name IN (" +
                "  SELECT constraint_name FROM information_schema.table_constraints " +
                "  WHERE table_name = 'classroom' AND constraint_type = 'PRIMARY KEY'" +
                ")",
                Integer.class
            );

            if (wrongFkCount != null && wrongFkCount > 0) {
                System.out.println("[DbMigrationRunner] Fixing student_classroom_id_fkey to reference 'classrooms' table...");
                jdbc.execute("ALTER TABLE student DROP CONSTRAINT student_classroom_id_fkey");
                jdbc.execute("ALTER TABLE student ADD CONSTRAINT student_classroom_id_fkey FOREIGN KEY (classroom_id) REFERENCES classrooms(id)");
                System.out.println("[DbMigrationRunner] FK constraint fixed successfully.");
            }
        } catch (Exception e) {
            System.err.println("[DbMigrationRunner] Could not verify/fix FK constraint: " + e.getMessage());
        }

        // Add pinned column to announcements
        try {
            Integer pinnedExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='announcements' AND column_name='pinned'",
                Integer.class);
            if (pinnedExists != null && pinnedExists == 0) {
                jdbc.execute("ALTER TABLE announcements ADD COLUMN pinned BOOLEAN NOT NULL DEFAULT false");
                System.out.println("[DbMigrationRunner] Added 'pinned' column to announcements.");
            }
        } catch (Exception e) {
            System.err.println("[DbMigrationRunner] Could not add pinned column: " + e.getMessage());
        }

        // Add image_url column to announcements
        try {
            Integer imgExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='announcements' AND column_name='image_url'",
                Integer.class);
            if (imgExists != null && imgExists == 0) {
                jdbc.execute("ALTER TABLE announcements ADD COLUMN image_url VARCHAR(500)");
                System.out.println("[DbMigrationRunner] Added 'image_url' column to announcements.");
            }
        } catch (Exception e) {
            System.err.println("[DbMigrationRunner] Could not add image_url column: " + e.getMessage());
        }

        // Create announcement_views table
        try {
            Integer avExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='announcement_views'",
                Integer.class);
            if (avExists != null && avExists == 0) {
                jdbc.execute(
                    "CREATE TABLE announcement_views (" +
                    "  id bigserial PRIMARY KEY," +
                    "  announcement_id bigint NOT NULL," +
                    "  viewer_account_id VARCHAR(255) NOT NULL," +
                    "  viewed_at timestamptz DEFAULT now()," +
                    "  UNIQUE(announcement_id, viewer_account_id)" +
                    ")");
                System.out.println("[DbMigrationRunner] Created announcement_views table.");
            }
        } catch (Exception e) {
            System.err.println("[DbMigrationRunner] Could not create announcement_views table: " + e.getMessage());
        }

        // Create classwork_completions table
        try {
            Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='classwork_completions'",
                Integer.class);
            if (exists != null && exists == 0) {
                jdbc.execute(
                    "CREATE TABLE classwork_completions (" +
                    "  id bigserial PRIMARY KEY," +
                    "  assignment_id bigint NOT NULL," +
                    "  student_id bigint NOT NULL," +
                    "  marked_by VARCHAR(255)," +
                    "  marked_at timestamptz DEFAULT now()," +
                    "  UNIQUE(assignment_id, student_id)" +
                    ")");
                System.out.println("[DbMigrationRunner] Created classwork_completions table.");
            }
        } catch (Exception e) {
            System.err.println("[DbMigrationRunner] Could not create classwork_completions table: " + e.getMessage());
        }

        // Drop legacy 'classroom' (singular) table — a dead leftover from before the entity
        // was renamed to 'classrooms' (plural). Nothing reads/writes it anymore, but its
        // lingering FK constraint (fk_classroom_teacher) was falsely blocking deletion of
        // teacher accounts that have zero real classrooms, since Postgres doesn't know the
        // table is abandoned. Dropping it removes the constraint too (CASCADE).
        try {
            Integer legacyExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='classroom'",
                Integer.class);
            if (legacyExists != null && legacyExists > 0) {
                jdbc.execute("DROP TABLE classroom CASCADE");
                System.out.println("[DbMigrationRunner] Dropped legacy 'classroom' (singular) table.");
            }
        } catch (Exception e) {
            System.err.println("[DbMigrationRunner] Could not drop legacy classroom table: " + e.getMessage());
        }

        // Create notifications table
        try {
            Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name='notifications'",
                Integer.class);
            if (exists != null && exists == 0) {
                jdbc.execute(
                    "CREATE TABLE notifications (" +
                    "  id bigserial PRIMARY KEY," +
                    "  account_id VARCHAR(255) NOT NULL," +
                    "  title VARCHAR(255) NOT NULL," +
                    "  body TEXT," +
                    "  is_read BOOLEAN NOT NULL DEFAULT FALSE," +
                    "  created_at TIMESTAMPTZ DEFAULT NOW()" +
                    ")");
                System.out.println("[DbMigrationRunner] Created notifications table.");
            }
        } catch (Exception e) {
            System.err.println("[DbMigrationRunner] Could not create notifications table: " + e.getMessage());
        }
    }
}

package com.streambid.user;

import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);
    private static final Field<String> EMAIL = DSL.field(DSL.name("email"), String.class);
    private static final Field<String> PASSWORD = DSL.field(DSL.name("password_hash"), String.class);
    private static final Field<String> NAME = DSL.field(DSL.name("display_name"), String.class);

    private final DSLContext database;

    public UserRepository(DSLContext database) {
        this.database = database;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return database.select(ID, EMAIL, PASSWORD, NAME)
                .from(DSL.table(DSL.name("users")))
                .where(EMAIL.eq(email))
                .fetchOptional(record -> new UserAccount(record.get(ID), record.get(EMAIL),
                        record.get(PASSWORD), record.get(NAME)));
    }

    public List<String> findRoles(UUID userId) {
        return database.select(DSL.field(DSL.name("role"), String.class))
                .from(DSL.table(DSL.name("user_roles")))
                .where(DSL.field(DSL.name("user_id"), UUID.class).eq(userId))
                .fetch(0, String.class);
    }
    public void addRole(UUID userId, UserRole role) {
        database.insertInto(DSL.table(DSL.name("user_roles")))
                .columns(DSL.field(DSL.name("user_id"), UUID.class),
                        DSL.field(DSL.name("role"), String.class))
                .values(userId, role.name())
                .execute();
    }
    public void save(UserAccount user) {
        database.insertInto(DSL.table(DSL.name("users")))
                .columns(ID, EMAIL, PASSWORD, NAME)
                .values(user.id(), user.email(), user.passwordHash(), user.displayName())
                .execute();
    }
}

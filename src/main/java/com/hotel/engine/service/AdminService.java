package com.hotel.engine.service;

import com.hotel.engine.domain.ConflictException;
import com.hotel.engine.domain.InvalidInputException;
import com.hotel.engine.domain.NotFoundException;
import com.hotel.engine.model.Enums.Role;
import com.hotel.engine.model.UserAccount;
import com.hotel.engine.repository.UserRepository;
import com.hotel.engine.security.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;

/** Staff accounts (admin only). Guards against locking everyone out: the last enabled admin stays an admin. */
@Service
public class AdminService {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]{3,30}");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    public record UserView(long id, String username, String displayName, Role role, boolean enabled) {
        static UserView of(UserAccount u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isEnabled());
        }
    }

    public List<UserView> list() {
        return users.findAll().stream().map(UserView::of)
                .sorted((a, b) -> a.username().compareTo(b.username())).toList();
    }

    @Transactional
    public UserView create(String username, String displayName, Role role, String password) {
        String name = username == null ? "" : username.trim().toLowerCase();
        if (!USERNAME.matcher(name).matches()) {
            throw new InvalidInputException("Usernames are 3 to 30 characters: lower-case letters, digits, dot, dash, underscore.");
        }
        if (users.existsByUsername(name)) {
            throw new ConflictException("The username " + name + " is taken.");
        }
        checkPassword(password);
        if (role == null) {
            throw new InvalidInputException("Choose a role.");
        }
        UserAccount u = users.save(new UserAccount(name, encoder.encode(password),
                displayName == null || displayName.isBlank() ? name : displayName.trim(), role));
        audit.log("USER_CREATED", "USER", name, "Role " + role);
        return UserView.of(u);
    }

    @Transactional
    public UserView update(long id, String displayName, Role role, Boolean enabled, String newPassword) {
        UserAccount u = users.findById(id).orElseThrow(() -> new NotFoundException("No user " + id + "."));
        boolean losingAdmin = u.getRole() == Role.ADMIN && u.isEnabled()
                && ((role != null && role != Role.ADMIN) || Boolean.FALSE.equals(enabled));
        if (losingAdmin && users.findAll().stream().filter(x -> x.getRole() == Role.ADMIN && x.isEnabled()).count() <= 1) {
            throw new ConflictException("This is the only enabled admin. Make another admin first.");
        }
        if (u.getUsername().equals(CurrentUser.name()) && Boolean.FALSE.equals(enabled)) {
            throw new ConflictException("You can't disable your own account.");
        }
        if (displayName != null && !displayName.isBlank()) {
            u.setDisplayName(displayName.trim());
        }
        if (role != null) {
            u.setRole(role);
        }
        if (enabled != null) {
            u.setEnabled(enabled);
        }
        if (newPassword != null && !newPassword.isEmpty()) {
            checkPassword(newPassword);
            u.setPasswordHash(encoder.encode(newPassword));
        }
        audit.log("USER_CHANGED", "USER", u.getUsername(), "Role " + u.getRole() + (u.isEnabled() ? "" : ", disabled")
                + (newPassword != null && !newPassword.isEmpty() ? ", password reset" : ""));
        return UserView.of(users.save(u));
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < 10 || password.length() > 72) {
            throw new InvalidInputException("Passwords need 10 to 72 characters.");
        }
    }
}

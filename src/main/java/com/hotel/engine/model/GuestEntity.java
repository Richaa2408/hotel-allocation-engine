package com.hotel.engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A guest's contact details. The *_key columns hold normalised values (lower-case email,
 * digits-only phone) used for duplicate checks and blocklist screening.
 */
@Entity
@Table(name = "guest")
public class GuestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String email;
    private String phone;

    @Column(name = "email_key")
    private String emailKey;

    @Column(name = "phone_key")
    private String phoneKey;

    protected GuestEntity() {
    }

    public GuestEntity(String fullName, String email, String phone, String emailKey, String phoneKey) {
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.emailKey = emailKey;
        this.phoneKey = phoneKey;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getEmailKey() { return emailKey; }
    public String getPhoneKey() { return phoneKey; }
}

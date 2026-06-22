package com.mytadika.dto;

import jakarta.validation.constraints.Size;

public class UpdateProfileRequestDTO {

    @Size(max = 100)
    private String fullName;

    @Size(max = 20)
    private String phoneNumber;

    @Size(max = 500)
    private String address;

    public UpdateProfileRequestDTO() {}

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}

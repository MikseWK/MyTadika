package com.mytadika.service;

import com.mytadika.dto.UpdateProfileRequest;
import com.mytadika.model.Account;
import com.mytadika.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Service
public class ProfileService {

    private final AccountRepository accountRepository;
    private final SupabaseStorageService storageService;

    public ProfileService(AccountRepository accountRepository, SupabaseStorageService storageService) {
        this.accountRepository = accountRepository;
        this.storageService = storageService;
    }

    public Map<String, Object> getProfile(String accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Map<String, Object> profile = new HashMap<>();
        profile.put("accountId", account.getAccountId());
        profile.put("fullName", account.getFullName());
        profile.put("email", account.getEmail());
        profile.put("phoneNumber", account.getPhoneNumber());
        profile.put("address", account.getAddress());
        profile.put("profileImageUrl", account.getProfileImageUrl());
        profile.put("roleType", account.getRoleType().name());
        return profile;
    }

    public void updateProfile(String accountId, UpdateProfileRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            account.setFullName(request.getFullName());
        }
        account.setPhoneNumber(request.getPhoneNumber());
        account.setAddress(request.getAddress());
        accountRepository.save(account);
    }

    public String uploadProfileImage(String accountId, MultipartFile file) throws Exception {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        String ext = getExtension(file.getOriginalFilename());
        String filename = "profile_" + accountId + "_" + System.currentTimeMillis() + ext;
        String imageUrl = storageService.uploadImage(file.getBytes(), filename, file.getContentType());

        account.setProfileImageUrl(imageUrl);
        accountRepository.save(account);

        return imageUrl;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }
}

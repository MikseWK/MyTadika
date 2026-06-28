package com.mytadika.service;

import com.mytadika.dto.UpdateProfileRequestDTO;
import com.mytadika.exception.InvalidInputException;
import com.mytadika.model.Account;
import com.mytadika.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final SupabaseStorageService storageService;

    public AccountService(AccountRepository accountRepository, SupabaseStorageService storageService) {
        this.accountRepository = accountRepository;
        this.storageService = storageService;
    }

    public Account updateProfile(Account currentAccount, UpdateProfileRequestDTO request) {
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            currentAccount.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            currentAccount.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getAddress() != null) {
            currentAccount.setAddress(request.getAddress());
        }
        return accountRepository.save(currentAccount);
    }

    public Account updateProfileImage(Account currentAccount, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("Please choose an image to upload.");
        }

        String filename = "profile_" + currentAccount.getAccountId() + "_" + System.currentTimeMillis()
                + extensionOf(file.getOriginalFilename());

        String imageUrl;
        try {
            imageUrl = storageService.uploadImage(file.getBytes(), filename, file.getContentType());
        } catch (java.io.IOException e) {
            throw new InvalidInputException("Could not read the uploaded image.");
        }

        currentAccount.setProfileImageUrl(imageUrl);
        return accountRepository.save(currentAccount);
    }

    private String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }
}

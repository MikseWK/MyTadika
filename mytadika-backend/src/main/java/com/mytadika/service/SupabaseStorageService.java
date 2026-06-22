package com.mytadika.service;

import com.mytadika.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.bucket:profile-images}")
    private String bucket;

    private final RestTemplate restTemplate = new RestTemplate();

    public String uploadImage(byte[] data, String filename, String contentType) {
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucket + "/" + filename;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + serviceRoleKey);
        headers.setContentType(MediaType.parseMediaType(contentType != null ? contentType : "image/jpeg"));

        try {
            restTemplate.exchange(uploadUrl, HttpMethod.POST, new HttpEntity<>(data, headers), String.class);
        } catch (RestClientException e) {
            throw new ExternalServiceException("Could not upload image to storage. Please try again.");
        }

        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + filename;
    }
}

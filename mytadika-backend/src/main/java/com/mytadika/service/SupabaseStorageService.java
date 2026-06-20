package com.mytadika.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
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
        headers.setContentType(MediaType.parseMediaType(
                contentType != null ? contentType : "image/jpeg"));

        HttpEntity<byte[]> entity = new HttpEntity<>(data, headers);
        restTemplate.exchange(uploadUrl, HttpMethod.POST, entity, String.class);

        return supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + filename;
    }
}

package com.huawei.ascend.examples.workmate.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public UserProfileDefinition getProfile() {
        return userProfileService.getProfile();
    }

    @PutMapping
    public UserProfileDefinition updateProfile(@RequestBody UserProfileDefinition profile) {
        return userProfileService.updateProfile(profile);
    }
}
